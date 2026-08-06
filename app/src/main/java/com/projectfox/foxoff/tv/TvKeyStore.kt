package com.projectfox.foxoff.tv

import android.content.Context
import android.util.Base64
import com.projectfox.foxoff.core.logging.FoxLogger
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509KeyManager
import javax.net.ssl.X509TrustManager
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils

/**
 * Manages persistent software-based TLS identity for Android TV Remote v2.
 * Mirroring exact androidtvremote2 (Home Assistant) certificate structure.
 */
class TvKeyStore(private val context: Context) {

    private val prefs = context.getSharedPreferences(
        "foxoff_tv_identity_rsa_v1",
        Context.MODE_PRIVATE
    )
    private var privateKey: PrivateKey? = null
    private var certificate: X509Certificate? = null

    init {
        loadOrGenerate()
    }

    private fun loadOrGenerate() {
        val privKeyB64 = prefs.getString("priv", null)
        val certB64 = prefs.getString("cert", null)

        if (privKeyB64 != null && certB64 != null) {
            try {
                val kf = KeyFactory.getInstance("RSA")
                privateKey = kf.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(privKeyB64, Base64.DEFAULT)))
                val cf = CertificateFactory.getInstance("X.509")
                certificate = cf.generateCertificate(ByteArrayInputStream(Base64.decode(certB64, Base64.DEFAULT))) as X509Certificate
                
                if (certificate?.publicKey != null && privateKey != null) {
                    FoxLogger.i("FOX-TV | TLS | Software identity loaded and verified.")
                    return
                }
            } catch (e: Exception) {
                FoxLogger.e("FOX-TV | TLS | Validation failed, regenerating...", e)
            }
        }
        generateNewIdentity()
    }

    private fun generateNewIdentity() {
        try {

            FoxLogger.i("FOX-TV | TLS | Generating new RSA 2048 identity..."
            )


            val kpg = KeyPairGenerator.getInstance("RSA")
            kpg.initialize(2048)
            val kp = kpg.generateKeyPair()


            val commonName = X500Name("CN=atvremote")

            val serial = BigInteger(64, SecureRandom()).abs()

            val notBefore = Date(System.currentTimeMillis() - 86400000)

            val notAfter = Date(
                notBefore.time + 3650L * 24 * 60 * 60 * 1000
            )


            val certBuilder = JcaX509v3CertificateBuilder(
                commonName,
                serial,
                notBefore,
                notAfter,
                commonName,
                kp.public
            )


            // 1. BasicConstraints : certificat final (pas CA)
            certBuilder.addExtension(
                Extension.basicConstraints,
                false,
                BasicConstraints(false)
            )


// 2. Key Usage compatible Android TV Remote
            certBuilder.addExtension(
                Extension.keyUsage,
                true,
                KeyUsage(
                    KeyUsage.digitalSignature or
                            KeyUsage.keyEncipherment
                )
            )


// 3. Client Authentication
            certBuilder.addExtension(
                Extension.extendedKeyUsage,
                false,
                ExtendedKeyUsage(
                    KeyPurposeId.id_kp_clientAuth
                )
            )


// 4. Subject Alternative Name
            certBuilder.addExtension(
                Extension.subjectAlternativeName,
                false,
                GeneralNames(
                    GeneralName(
                        GeneralName.dNSName,
                        "atvremote"
                    )
                )
            )


// 5. Subject Key Identifier + Authority Key Identifier
            val extUtils = JcaX509ExtensionUtils()

            certBuilder.addExtension(
                Extension.subjectKeyIdentifier,
                false,
                extUtils.createSubjectKeyIdentifier(kp.public)
            )

            certBuilder.addExtension(
                Extension.authorityKeyIdentifier,
                false,
                extUtils.createAuthorityKeyIdentifier(kp.public)
            )



            val signer =
                JcaContentSignerBuilder("SHA256withRSA")
                    .build(kp.private)


            val certHolder = certBuilder.build(signer)

            val cert =
                JcaX509CertificateConverter()
                    .getCertificate(certHolder)



            privateKey = kp.private
            certificate = cert


            prefs.edit()
                .putString(
                    "priv",
                    Base64.encodeToString(
                        kp.private.encoded,
                        Base64.NO_WRAP
                    )
                )
                .putString(
                    "cert",
                    Base64.encodeToString(
                        cert.encoded,
                        Base64.NO_WRAP
                    )
                )
                .apply()


            FoxLogger.i("FOX-TV | TLS | Generating new RSA 2048 identity...")

        } catch(e: Exception){

            FoxLogger.e(
                "FOX-TV | TLS | Identity generation failed",
                e
            )
        }
    }

    fun getSslContext(): SSLContext {
        val cert = certificate ?: throw IllegalStateException("Certificate is NULL")
        val priv = privateKey ?: throw IllegalStateException("PrivateKey is NULL")

        // 1. Creation of an in-memory KeyStore
        val memoryKs = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
            load(null, null)
            setKeyEntry("identity", priv, "".toCharArray(), arrayOf(cert))
        }

        // 2. Creation of a standard KeyManagerFactory
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(memoryKs, "".toCharArray())
        val keyManagers = kmf.keyManagers

        try {
            FoxLogger.i("FOX-TV | Diagnostic | Entering KeyManagers loop (Count: ${keyManagers.size})")
            keyManagers.forEachIndexed { index, km ->
                FoxLogger.i("FOX-TV | Diagnostic | Checking KeyManager[$index]: ${km.javaClass.name}")
                if (km is X509KeyManager) {
                    FoxLogger.i("FOX-TV | Diagnostic | Found X509KeyManager")
                    FoxLogger.i("FOX-TV | ===== KeyManager Diagnostic =====")

                    FoxLogger.i("FOX-TV | Diagnostic | Calling getClientAliases(ALL)...")
                    val aliases = km.getClientAliases("RSA", null)
                    FoxLogger.i("FOX-TV | Diagnostic | ClientAliases(EC) = ${aliases?.joinToString()}")
                    
                    val alias = aliases?.firstOrNull()
                    FoxLogger.i("FOX-TV | Diagnostic | Selected alias = $alias")
                    
                    if (alias != null) {
                        FoxLogger.i("FOX-TV | Diagnostic | Calling getCertificateChain($alias)...")
                        val chain = km.getCertificateChain(alias)
                        FoxLogger.i("FOX-TV | Diagnostic | Chain size = ${chain?.size}")
                        FoxLogger.i("FOX-TV | Diagnostic | Subject = ${chain?.firstOrNull()?.subjectX500Principal}")
                        FoxLogger.i("FOX-TV | Diagnostic | Issuer = ${chain?.firstOrNull()?.issuerX500Principal}")
                        FoxLogger.i("FOX-TV | Diagnostic | BasicConstraints = ${chain?.firstOrNull()?.basicConstraints}")
                        
                        FoxLogger.i("FOX-TV | Diagnostic | Calling getPrivateKey($alias)...")
                        val pk = km.getPrivateKey(alias)
                        FoxLogger.i("FOX-TV | Diagnostic | PrivateKey = ${pk?.algorithm}")
                        
                        if (chain != null && chain.isNotEmpty()) {
                            FoxLogger.i("FOX-TV | Diagnostic | Calculating fingerprint...")
                            val md = MessageDigest.getInstance("SHA-256")
                            val fp = md.digest(chain[0].encoded).joinToString("") { b -> "%02x".format(b) }
                            FoxLogger.i("FOX-TV | Diagnostic | Fingerprint = $fp")
                        }
                    }
                } else {
                    FoxLogger.i("FOX-TV | Diagnostic | KeyManager is NOT an X509KeyManager")
                }
            }
            FoxLogger.i("FOX-TV | Diagnostic | KeyManagers loop finished successfully")
        } catch (e: Exception) {
            FoxLogger.e("FOX-TV | Diagnostic | KeyManager diagnostic crashed", e)
        }

        val baseKm = keyManagers.first { it is X509KeyManager } as X509KeyManager
        val diagnosticKm = object : X509KeyManager {
            override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out java.security.Principal>?, socket: java.net.Socket?): String? {
                val alias = baseKm.chooseClientAlias(keyType, issuers, socket) ?: "identity"
                FoxLogger.i("FOX-TV | mTLS Audit | chooseClientAlias: $alias (Requested KeyTypes: ${keyType?.joinToString()}, Issuers: ${issuers?.size ?: 0})")
                return alias
            }
            override fun getCertificateChain(alias: String?): Array<X509Certificate>? = baseKm.getCertificateChain(alias)
            override fun getPrivateKey(alias: String?): java.security.PrivateKey? = baseKm.getPrivateKey(alias)
            override fun getClientAliases(keyType: String?, issuers: Array<out java.security.Principal>?): Array<String>? = baseKm.getClientAliases(keyType, issuers)
            override fun getServerAliases(keyType: String?, issuers: Array<out java.security.Principal>?): Array<String>? = baseKm.getServerAliases(keyType, issuers)
            override fun chooseServerAlias(keyType: String?, issuers: Array<out java.security.Principal>?, socket: java.net.Socket?): String? = baseKm.chooseServerAlias(keyType, issuers, socket)
        }

        val sc = SSLContext.getInstance("TLS")
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(c: Array<out X509Certificate>?, a: String?) {}
            override fun checkServerTrusted(c: Array<out X509Certificate>?, a: String?) {
                c?.firstOrNull()?.let {
                    val fingerprint = MessageDigest.getInstance("SHA-256").digest(it.encoded).joinToString("") { b -> "%02x".format(b) }
                    FoxLogger.i("FOX-TV | TLS | Server Fingerprint: $fingerprint")
                }
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
        
        sc.init(arrayOf(diagnosticKm), arrayOf(tm), SecureRandom())
        FoxLogger.i("FOX-TV | TLS | SSLContext initialized successfully")
        return sc
    }

    fun getClientPublicKey(): ByteArray {

        val pub = certificate?.publicKey ?: return byteArrayOf()

        return when (pub) {

            is java.security.interfaces.RSAPublicKey -> {

                val modulus = pub.modulus
                    .toByteArray()
                    .stripLeadingZero()

                byteArrayOf(0x00) + modulus
            }

            else -> {
                FoxLogger.e(
                    "FOX-TV | TLS | Public key is not RSA"
                )
                byteArrayOf()
            }
        }
    }
    fun getClientModulus(): ByteArray {

        val pub = certificate?.publicKey

        if (pub !is java.security.interfaces.RSAPublicKey) {
            FoxLogger.e("FOX-TV | TLS | Not RSA key")
            return byteArrayOf()
        }

        return pub.modulus
            .toByteArray()
            .stripLeadingZero()
    }


    fun getClientExponent(): ByteArray {

        val pub = certificate?.publicKey

        if (pub !is java.security.interfaces.RSAPublicKey) {
            FoxLogger.e("FOX-TV | TLS | Not RSA key")
            return byteArrayOf()
        }

        return pub.publicExponent
            .toByteArray()
            .stripLeadingZero()
    }
    private fun ByteArray.stripLeadingZero(): ByteArray = if (this.isNotEmpty() && this[0] == 0.toByte()) this.drop(1).toByteArray() else this

    fun savePairingKey(deviceId: String, key: String) {
        context.getSharedPreferences("foxoff_tv_pairs", Context.MODE_PRIVATE).edit().putString("pairing_$deviceId", key).apply()
    }

    fun getPairingKey(deviceId: String): String? = context.getSharedPreferences("foxoff_tv_pairs", Context.MODE_PRIVATE).getString("pairing_$deviceId", null)
    
    /**
     * TEMPORARY: Export current certificate for external debugging.
     */
    fun exportCertificate(): java.io.File? {
        val cert = certificate ?: return null
        return try {
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            val file = java.io.File(dir, "foxoff.der")
            file.writeBytes(cert.encoded)
            FoxLogger.i(
                "FOX-TV | Certificate exported to: ${file.absolutePath}"
            )
            file
        } catch (e: Exception) {
            FoxLogger.e("FOX-TV | Failed to export certificate", e)
            null
        }
    }
    fun getCertificateBytes(): ByteArray {
        return certificate?.encoded ?: byteArrayOf()
    }
    fun logTlsDetails(socket: SSLSocket) {
        certificate?.let { cert ->
            val pubKey = cert.publicKey
            FoxLogger.i("FOX-TV | TLS | Cert Subject: ${cert.subjectDN}")
            FoxLogger.i("FOX-TV | TLS | Cert Issuer: ${cert.issuerDN}")
            FoxLogger.i("FOX-TV | TLS | Version: ${cert.version}")
            FoxLogger.i("FOX-TV | TLS | IsCA: ${cert.basicConstraints}")
            FoxLogger.i("FOX-TV | TLS | Signature Algorithm: ${cert.sigAlgName}")
            FoxLogger.i("FOX-TV | TLS | Public Key Algorithm: ${cert.publicKey.algorithm}")
            FoxLogger.i("FOX-TV | TLS | Public Key Format: ${pubKey.algorithm}")
            FoxLogger.i("FOX-TV | TLS | Basic Constraints: ${cert.basicConstraints}")
            FoxLogger.i("FOX-TV | TLS | Key Usage: ${cert.keyUsage?.joinToString()}")
            
            val eku = try { cert.extendedKeyUsage } catch (e: Exception) { null }
            FoxLogger.i("FOX-TV | TLS | Extended Key Usage: ${eku?.joinToString() ?: "None"}")
            
            val ski = cert.getExtensionValue("2.5.29.14")?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
            val aki = cert.getExtensionValue("2.5.29.35")?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
            
            FoxLogger.i("FOX-TV | TLS | SubjectKeyIdentifier: ${ski ?: "None"}")
            FoxLogger.i("FOX-TV | TLS | AuthorityKeyIdentifier: ${aki ?: "None"}")
            FoxLogger.i("FOX-TV | TLS | EnabledProtocols: ${socket.enabledProtocols.joinToString()}")
            FoxLogger.i("FOX-TV | TLS | EnabledCipherSuites: ${socket.enabledCipherSuites.joinToString()}")
        }
    }
}