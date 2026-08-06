package com.projectfox.foxoff.tv.pairing

import android.content.Context
import android.util.Base64
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

class TvIdentity(
    private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "tv_lab_identity_v1"
        private const val PRIVATE_KEY_PREF = "private_key"
        private const val CERTIFICATE_PREF = "certificate"

        // Nom utilisé par l’implémentation de référence.
        private const val HOSTNAME = "atvremote"
    }

    private val prefs = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private var privateKey: PrivateKey
    private var certificate: X509Certificate

    init {
        val identity = loadIdentity() ?: generateIdentity()

        privateKey = identity.first
        certificate = identity.second
    }

    private fun loadIdentity(): Pair<PrivateKey, X509Certificate>? {
        val privateKeyBase64 =
            prefs.getString(PRIVATE_KEY_PREF, null) ?: return null

        val certificateBase64 =
            prefs.getString(CERTIFICATE_PREF, null) ?: return null

        return try {
            val privateKeyBytes = Base64.decode(
                privateKeyBase64,
                Base64.DEFAULT
            )

            val certificateBytes = Base64.decode(
                certificateBase64,
                Base64.DEFAULT
            )

            val keyFactory = KeyFactory.getInstance("RSA")
            val loadedPrivateKey = keyFactory.generatePrivate(
                PKCS8EncodedKeySpec(privateKeyBytes)
            )

            val certificateFactory =
                CertificateFactory.getInstance("X.509")

            val loadedCertificate =
                certificateFactory.generateCertificate(
                    ByteArrayInputStream(certificateBytes)
                ) as X509Certificate

            loadedPrivateKey to loadedCertificate
        } catch (_: Exception) {
            prefs.edit().clear().apply()
            null
        }
    }

    private fun generateIdentity(): Pair<PrivateKey, X509Certificate> {
        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048, SecureRandom())

        val keyPair = keyPairGenerator.generateKeyPair()

        val name = X500Name("CN=$HOSTNAME")

        val now = System.currentTimeMillis()
        val notBefore = Date(now)
        val notAfter = Date(
            now + 3650L * 24L * 60L * 60L * 1000L
        )

        val certificateBuilder = JcaX509v3CertificateBuilder(
            name,
            BigInteger.valueOf(1000L),
            notBefore,
            notAfter,
            name,
            keyPair.public
        )

        // Équivalent à :
        // BasicConstraints(ca=True, path_length=0)
        certificateBuilder.addExtension(
            Extension.basicConstraints,
            false,
            BasicConstraints(0)
        )

        certificateBuilder.addExtension(
            Extension.subjectAlternativeName,
            false,
            GeneralNames(
                GeneralName(
                    GeneralName.dNSName,
                    HOSTNAME
                )
            )
        )

        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .build(keyPair.private)

        val certificate = JcaX509CertificateConverter()
            .getCertificate(certificateBuilder.build(signer))

        certificate.checkValidity()
        certificate.verify(keyPair.public)

        prefs.edit()
            .putString(
                PRIVATE_KEY_PREF,
                Base64.encodeToString(
                    keyPair.private.encoded,
                    Base64.NO_WRAP
                )
            )
            .putString(
                CERTIFICATE_PREF,
                Base64.encodeToString(
                    certificate.encoded,
                    Base64.NO_WRAP
                )
            )
            .apply()

        return keyPair.private to certificate
    }

    fun createSslContext(): SSLContext {
        val password = CharArray(0)

        val keyStore = KeyStore.getInstance(
            KeyStore.getDefaultType()
        ).apply {
            load(null, null)

            setKeyEntry(
                "identity",
                privateKey,
                password,
                arrayOf(certificate)
            )
        }

        val keyManagerFactory = KeyManagerFactory.getInstance(
            KeyManagerFactory.getDefaultAlgorithm()
        ).apply {
            init(keyStore, password)
        }

        /*
         * Pour le laboratoire uniquement :
         * la TV utilise elle aussi un certificat auto-signé.
         *
         * Après validation du pairing, nous remplacerons ceci
         * par une mémorisation/vérification de l’empreinte.
         */
        val trustManager = object : X509TrustManager {
            override fun checkClientTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?
            ) = Unit

            override fun checkServerTrusted(
                chain: Array<out X509Certificate>?,
                authType: String?
            ) = Unit

            override fun getAcceptedIssuers(): Array<X509Certificate> =
                emptyArray()
        }

        return SSLContext.getInstance("TLS").apply {
            init(
                keyManagerFactory.keyManagers,
                arrayOf(trustManager),
                SecureRandom()
            )
        }
    }
    fun getCertificate(): X509Certificate {
        return certificate
    }
}