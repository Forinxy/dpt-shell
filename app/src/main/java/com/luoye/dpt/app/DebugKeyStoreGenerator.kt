package com.luoye.dpt.app

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import java.io.File
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Generates a self-signed PKCS12 debug keystore at runtime.
 * Android's default providers do not support JKS, so a PKCS12 keystore is
 * generated and injected into the signing pipeline via
 * [com.luoye.dpt.builder.AndroidPackage.setDebugKeyStorePath] and
 * [com.luoye.dpt.builder.AndroidPackage.setKeyStoreType].
 */
object DebugKeyStoreGenerator {

    private const val ALIAS = "key0"
    private const val PASSWORD = "android"
    private const val VALIDITY_YEARS = 100L

    fun ensure(file: File): File {
        if (file.exists() && file.length() > 0) {
            return file
        }
        generate(file)
        return file
    }

    private fun generate(file: File) {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(BouncyCastleProvider())
        }

        val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
        keyPairGenerator.initialize(2048, SecureRandom())
        val keyPair = keyPairGenerator.generateKeyPair()

        val dn = X500Name("CN=dpt-shell, OU=MonkeyCode, O=MonkeyCode, C=CN")
        val notBefore = Date()
        val notAfter = Date(notBefore.time + VALIDITY_YEARS * 365L * 24L * 60L * 60L * 1000L)
        val serial = BigInteger(128, SecureRandom())

        val certBuilder = JcaX509v3CertificateBuilder(
            dn, serial, notBefore, notAfter, dn, keyPair.public
        )
        val signerBuilder = JcaContentSignerBuilder("SHA256withRSA")
        val cert = JcaX509CertificateConverter()
            .getCertificate(certBuilder.build(signerBuilder.build(keyPair.private)))

        val keyStore = KeyStore.getInstance("PKCS12")
        keyStore.load(null, null)
        keyStore.setKeyEntry(ALIAS, keyPair.private, PASSWORD.toCharArray(), arrayOf<X509Certificate>(cert))

        file.parentFile?.mkdirs()
        FileOutputStream(file).use { fos ->
            keyStore.store(fos, PASSWORD.toCharArray())
        }
    }
}
