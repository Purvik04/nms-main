package org.example.utils;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.xerial.snappy.Snappy;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Arrays;

/**
 * Utility class providing methods for compressing and encrypting JSON data using Snappy and AES_TRNASFORMATION-GCM,
 * and writing the results to a file or reading/decrypting a single line of base64 encoded input.
 */
public class SecureCompressor
{
    // Private constructor to prevent instantiation
    private SecureCompressor(){}

    // AES-GCM encryption transformation
    private static final String AES_TRANSFORMATION = "AES/GCM/NoPadding";

    private static final String ENCRYPTION_ALGORITHM_AES = "AES";

    // 256-bit AES key in byte array form, decoded from hex
    private static final byte[] key = hexStringToByteArray("0123456789abcdef0123456789abcdef");

    /**
     * Compresses a JSON array using Snappy, encrypts it using AES-GCM,
     * Base64-encodes the result, and writes it to a file.
     *
     * @param devices  the JSON array of device data to compress and encrypt
     * @param filePath the output file path to write the Base64-encoded encrypted data
     * @throws NoSuchPaddingException             if padding mechanism is not available
     * @throws NoSuchAlgorithmException           if AES-GCM algorithm is not available
     * @throws InvalidAlgorithmParameterException if the GCM spec is invalid
     * @throws InvalidKeyException                if the AES key is invalid
     * @throws IOException                        if writing to file or compression fails
     * @throws IllegalBlockSizeException          if cipher block size is invalid
     * @throws BadPaddingException                if padding is incorrect during encryption
     */
    public static void writeIntoFile(JsonArray devices, String filePath) throws NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException,
            IOException, IllegalBlockSizeException, BadPaddingException
    {
        // Generate a fresh 12-byte nonce for GCM
        var nonce = new byte[12];

        new SecureRandom().nextBytes(nonce);

        // Create new cipher instance
        var cipher = Cipher.getInstance(AES_TRANSFORMATION);

        // Initialize cipher for encryption with secret key and generated nonce
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, ENCRYPTION_ALGORITHM_AES), new GCMParameterSpec(128, nonce));

        // Compress the JSON array bytes using Snappy
        var encrypted = cipher.doFinal(Snappy.compress(devices.toBuffer().getBytes()));

        // Concatenate nonce + encrypted data
        var fullBytes = new byte[nonce.length + encrypted.length];

        System.arraycopy(nonce, 0, fullBytes, 0, nonce.length);

        System.arraycopy(encrypted, 0, fullBytes, nonce.length, encrypted.length);

        // Write Base64-encoded full byte array to the output file
        try (var out = new FileOutputStream(filePath))
        {
            out.write(Base64.getEncoder().encodeToString(fullBytes).getBytes());
        }
    }

    /**
     * Decrypts a Base64-encoded, AES-GCM-encrypted, Snappy-compressed line,
     * and returns the resulting JSON object.
     *
     * @param base64Line the input line to decode and decrypt
     * @return the resulting JSON object after decryption and decompression
     * @throws NoSuchPaddingException             if padding mechanism is not available
     * @throws NoSuchAlgorithmException           if AES/GCM algorithm is not available
     * @throws InvalidAlgorithmParameterException if the GCM spec is invalid
     * @throws InvalidKeyException                if the AES key is invalid
     * @throws IllegalBlockSizeException          if block size is incorrect
     * @throws BadPaddingException                if padding is incorrect during decryption
     * @throws IOException                        if decompression fails
     */
    public static JsonObject decryptPluginOutput(String base64Line) throws NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException,
            IOException
    {
        // Decode the base64 input to get the raw bytes
        var input = Base64.getDecoder().decode(base64Line);

        // Extract the first 12 bytes as the nonce
        var nonce = Arrays.copyOfRange(input, 0, 12);

        // Create and initialize cipher for decryption
        var cipher = Cipher.getInstance(AES_TRANSFORMATION);

        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, ENCRYPTION_ALGORITHM_AES), new GCMParameterSpec(128, nonce));

        // Decrypt and decompress the payload
        var decryptedBytes = cipher.doFinal(Arrays.copyOfRange(input, 12, input.length));

        return new JsonObject(new String(Snappy.uncompress(decryptedBytes), StandardCharsets.UTF_8));
    }

    /**
     * Converts a hex-encoded string to a byte array.
     *
     * @param hexKey the hex-encoded key string (e.g., "0123456789abcdef...")
     * @return the decoded byte array
     */
    private static byte[] hexStringToByteArray(String hexKey)
    {
        var data = new byte[hexKey.length() / 2];

        for (var index = 0; index < hexKey.length(); index += 2)
        {
            data[index / 2] = (byte) ((Character.digit(hexKey.charAt(index), 16) << 4)
                    + Character.digit(hexKey.charAt(index + 1), 16));
        }

        return data;
    }
}
