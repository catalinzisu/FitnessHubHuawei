package ro.fitnesshub.server

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwk.SigningKeyNotFoundException
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.Base64
import java.util.Date

/**
 * Fake "Google": an RSA key pair made for the tests. Tokens signed with it have exactly the shape
 * of Firebase ID tokens, and [config] makes the server trust this key instead of Google's.
 * So the tests run the real JWT check, without network and without a real Firebase account.
 */
object TestTokens {

    const val PROJECT = "test-project"
    const val KEY_ID = "test-key"

    private fun newKeyPair(): KeyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

    private val trusted = newKeyPair()

    /** A key the server does not trust (an attacker's). */
    val untrusted = newKeyPair()

    /** Answers like Google's key endpoint: the public key for [KEY_ID], nothing else. */
    private val keys = JwkProvider { keyId ->
        if (keyId != KEY_ID) throw SigningKeyNotFoundException("No key $keyId", null)
        val public = trusted.public as RSAPublicKey
        Jwk.fromValues(
            mapOf(
                "kid" to KEY_ID,
                "kty" to "RSA",
                "alg" to "RS256",
                "use" to "sig",
                "n" to base64Url(public.modulus),
                "e" to base64Url(public.publicExponent),
            ),
        )
    }

    val config = FirebaseAuthConfig(PROJECT, keys)

    /** A token like Firebase issues; every argument can be changed to make a bad one. */
    fun token(
        uid: String,
        issuer: String = "https://securetoken.google.com/$PROJECT",
        audience: String = PROJECT,
        expiresInSeconds: Long = 3600,
        signWith: KeyPair = trusted,
        keyId: String = KEY_ID,
    ): String {
        val now = System.currentTimeMillis()
        return JWT.create()
            .withKeyId(keyId)
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(uid)
            .withIssuedAt(Date(now - 60_000))
            .withExpiresAt(Date(now + expiresInSeconds * 1000))
            .sign(Algorithm.RSA256(signWith.public as RSAPublicKey, signWith.private as RSAPrivateKey))
    }

    private fun base64Url(value: BigInteger): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray())
}
