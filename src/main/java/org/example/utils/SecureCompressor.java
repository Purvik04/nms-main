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

public class SecureCompressor
{
    private SecureCompressor(){}

    private static final String AES = "AES/GCM/NoPadding";

    private static final byte[] key = hexStringToByteArray("0123456789abcdef0123456789abcdef");

    public static void writeEncryptedSnappyFile(JsonArray devices, String filePath) throws NoSuchPaddingException,
            NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException,
            IOException, IllegalBlockSizeException, BadPaddingException
    {
        var nonce = new byte[12];

        new SecureRandom().nextBytes(nonce);

        // why to generate a new cipher every time?
        // because we generate a new nonce every time

        var cipher = Cipher.getInstance(AES);

        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));

        var encrypted = cipher.doFinal(Snappy.compress(devices.toBuffer().getBytes()));

        var fullBytes = new byte[nonce.length + encrypted.length];

        System.arraycopy(nonce, 0, fullBytes, 0, nonce.length);

        System.arraycopy(encrypted, 0, fullBytes, nonce.length, encrypted.length);

        try (var out = new FileOutputStream(filePath))
        {
            out.write(Base64.getEncoder().encodeToString(fullBytes).getBytes());
        }
    }

    public static JsonObject decryptLine(String base64Line) throws NoSuchPaddingException, NoSuchAlgorithmException,
            InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException,
            IOException
    {
        var input = Base64.getDecoder().decode(base64Line);

        var nonce = Arrays.copyOfRange(input, 0, 12);

        var cipher = Cipher.getInstance(AES);

        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));

        return new JsonObject(new String(Snappy.uncompress(cipher.doFinal(Arrays.copyOfRange(input, 12, input.length))), StandardCharsets.UTF_8));
    }

    private static byte[] hexStringToByteArray(String hexKey)
    {
        var data = new byte[hexKey.length() / 2];

        for (var i = 0; i < hexKey.length(); i += 2)
            data[i / 2] = (byte) ((Character.digit(hexKey.charAt(i), 16) << 4) + Character.digit(hexKey.charAt(i + 1), 16));

        return data;
    }
}
