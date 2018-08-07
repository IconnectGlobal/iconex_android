package loopchain.icon.wallet.service.crypto;

import android.util.Log;

import com.google.gson.JsonObject;

import org.spongycastle.crypto.digests.SHA256Digest;
import org.spongycastle.crypto.generators.PKCS5S2ParametersGenerator;
import org.spongycastle.crypto.generators.SCrypt;
import org.spongycastle.crypto.params.KeyParameter;
import org.spongycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.UUID;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import loopchain.icon.wallet.core.Constants;

public class KeyStoreUtils {

    private static final String TAG = KeyStoreUtils.class.getSimpleName();

    public static final String PROVIDER = "SC";
    public static final int PBE_DKLEN = 32;
    public static final String PBE_CIPHER = "AES/CTR/NoPadding";
    public static final String PBE_MAC_KECCAK = "Keccak-256";
    public static final String PBE_MAC_SHA3 = "SHA3-256";

    public static final int PBKDF2_COUNT = 16384;

//    static {
//        Security.insertProviderAt(new org.spongycastle.jce.provider.BouncyCastleProvider(), 1);
//    }

    public static byte[] decryptPrivateKey(String password, String address, JsonObject crypto, String coinType) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        String strCipherText = crypto.get("ciphertext").getAsString();

        JsonObject cipherParams = crypto.getAsJsonObject("cipherparams");
        String strIv = cipherParams.get("iv").getAsString();

        String kdf = crypto.get("kdf").getAsString();
        JsonObject kdfParams = crypto.getAsJsonObject("kdfparams");
        String mac = crypto.get("mac").getAsString();

        if (kdf.equalsIgnoreCase("pbkdf2")) {
            int dkLen = kdfParams.get("dklen").getAsInt();
            String strSalt = kdfParams.get("salt").getAsString();
            int count = kdfParams.get("c").getAsInt();

            byte[] enc = Hex.decode(strCipherText);
            byte[] iv = Hex.decode(strIv);
            byte[] salt = Hex.decode(strSalt);
            byte[] devKey = pbkdf2(password.toCharArray(), dkLen, count, salt);
            byte[][] decrypted = null;
            if (coinType.equalsIgnoreCase(Constants.KS_COINTYPE_ICX)) {
                decrypted = ksDecrypt(devKey, enc, dkLen, iv, salt, PBE_MAC_KECCAK);
            } else {
                decrypted = ksDecrypt(devKey, enc, dkLen, iv, salt, PBE_MAC_KECCAK);
            }


            String newMac = Hex.toHexString(decrypted[1]);
            if (!newMac.equalsIgnoreCase(mac)) {
                System.out.println("Invalid Mac");
                return null;
            }

            byte[] publicKey = PKIUtils.getPublicKeyFromPrivateKey(decrypted[0], false);
            String newAddress = null;
            if (coinType.equalsIgnoreCase(Constants.KS_COINTYPE_ICX)) {
                newAddress = PKIUtils.makeAddress(publicKey);
            } else {
                newAddress = PKIUtils.makeEtherAddress(publicKey);
            }
            if (!newAddress.equalsIgnoreCase(address)) {
                System.out.println("Invalid Address(" + newAddress + ", " + address + ")");
                return null;
            }
            return decrypted[0];
        } else if (kdf.equalsIgnoreCase("scrypt")) {
            int dkLen = kdfParams.get("dklen").getAsInt();
            String strSalt = kdfParams.get("salt").getAsString();
            int n = kdfParams.get("n").getAsInt();
            int r = kdfParams.get("r").getAsInt();
            int p = kdfParams.get("p").getAsInt();

            byte[] enc = Hex.decode(strCipherText);
            byte[] iv = Hex.decode(strIv);
            byte[] salt = Hex.decode(strSalt);
            byte[] devKey = scrypt(password.toCharArray(), dkLen, n, r, p, salt);
            byte[][] decrypted = null;
            if (coinType.equalsIgnoreCase(Constants.KS_COINTYPE_ICX)) {
                decrypted = ksDecrypt(devKey, enc, dkLen, iv, salt, PBE_MAC_KECCAK);
            } else {
                decrypted = ksDecrypt(devKey, enc, dkLen, iv, salt, PBE_MAC_KECCAK);
            }

            String newMac = Hex.toHexString(decrypted[1]);
            if (!newMac.equalsIgnoreCase(mac)) {
                System.out.println("Invalid Mac");
                return null;
            }
            byte[] publicKey = PKIUtils.getPublicKeyFromPrivateKey(decrypted[0], false);
            String newAddress = null;
            if (coinType.equalsIgnoreCase(Constants.KS_COINTYPE_ICX)) {
                newAddress = PKIUtils.makeAddress(publicKey);
            } else {
                newAddress = PKIUtils.makeEtherAddress(publicKey);
            }
            if (!newAddress.equalsIgnoreCase(address)) {
                System.out.println("Invalid Address");
                return null;
            }
            return decrypted[0];
        } else {
            return null;
        }
    }

    public static byte[] pbkdf2(char[] pw, int dkLen, int count, byte[] salt) {
        byte[] passBytes = PKCS5S2ParametersGenerator.PKCS5PasswordToUTF8Bytes(pw);

        PKCS5S2ParametersGenerator gen = new PKCS5S2ParametersGenerator(new SHA256Digest());
        gen.init(passBytes, salt, count);
        KeyParameter parameter = (KeyParameter) gen.generateDerivedParameters(256);
        return parameter.getKey();
    }

    public static byte[] scrypt(char[] pw, int dkLen, int n, int r, int p, byte[] salt) {
        byte[] passBytes = PKCS5S2ParametersGenerator.PKCS5PasswordToUTF8Bytes(pw);

        return SCrypt.generate(passBytes, salt, n, r, p, 32);
    }

    public static String generateMac(byte[] mKey, byte[] eData, String hashAlgorithm) throws NoSuchAlgorithmException, NoSuchProviderException {
        byte[] checkMac = new byte[mKey.length + eData.length];
        System.arraycopy(mKey, 0, checkMac, 0, mKey.length);
        System.arraycopy(eData, 0, checkMac, mKey.length, eData.length);
        System.out.println("Mac Input: " + Hex.toHexString(checkMac));

        MessageDigest md = MessageDigest.getInstance(hashAlgorithm, PROVIDER);
        byte[] digest = md.digest(checkMac);
        return Hex.toHexString(digest);
    }

    /**
     * @param devKey  EncKey
     * @param data    private key
     * @param iv
     * @param salt
     * @param macAlgo
     * @return
     * @throws InvalidKeyException
     * @throws InvalidAlgorithmParameterException
     * @throws NoSuchAlgorithmException
     * @throws NoSuchProviderException
     * @throws NoSuchPaddingException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     */
    public static String[] ksEncrypt(byte[] devKey, byte[] data, byte[] iv, byte[] salt, String macAlgo) throws InvalidKeyException, InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {
//		byte[] pbkdfKey = pbkdf2(pw, PBE_DKLEN, count, salt);
        byte[] eKey = new byte[PBE_DKLEN / 2];
        byte[] mKey = new byte[PBE_DKLEN / 2];
        System.arraycopy(devKey, 0, eKey, 0, eKey.length);
        System.arraycopy(devKey, eKey.length, mKey, 0, mKey.length);

        Key aesKey = new SecretKeySpec(eKey, "AES");
        IvParameterSpec ivParam = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance(PBE_CIPHER, PROVIDER);
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, ivParam);
        byte[] enc = cipher.doFinal(data);

        byte[] mac = new byte[mKey.length + enc.length];
        System.arraycopy(mKey, 0, mac, 0, mKey.length);
        System.arraycopy(enc, 0, mac, mKey.length, enc.length);

        MessageDigest md = MessageDigest.getInstance(macAlgo, PROVIDER);
        byte[] digest = md.digest(mac);

        return new String[]{Hex.toHexString(enc), Hex.toHexString(digest)};
    }

    /**
     * @param devKey  EncKey
     * @param enc     cipher text
     * @param dkLen
     * @param iv
     * @param salt
     * @param macAlgo
     * @return
     * @throws NoSuchAlgorithmException
     * @throws NoSuchProviderException
     * @throws NoSuchPaddingException
     * @throws InvalidKeyException
     * @throws InvalidAlgorithmParameterException
     * @throws IllegalBlockSizeException
     * @throws BadPaddingException
     */
    public static byte[][] ksDecrypt(byte[] devKey, byte[] enc, int dkLen, byte[] iv, byte[] salt, String macAlgo) throws NoSuchAlgorithmException, NoSuchProviderException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
//		byte[] pbkdfKey = pbkdf2(pw, dkLen, count, salt);
        byte[] eKey = new byte[dkLen / 2];
        byte[] mKey = new byte[dkLen / 2];
        System.arraycopy(devKey, 0, eKey, 0, eKey.length);
        System.arraycopy(devKey, eKey.length, mKey, 0, mKey.length);

        Key aesKey = new SecretKeySpec(eKey, "AES");
        IvParameterSpec ivParam = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance(PBE_CIPHER, PROVIDER);
        cipher.init(Cipher.DECRYPT_MODE, aesKey, ivParam);
        byte[] dec = cipher.doFinal(enc);

        byte[] mac = new byte[mKey.length + enc.length];
        System.arraycopy(mKey, 0, mac, 0, mKey.length);
        System.arraycopy(enc, 0, mac, mKey.length, enc.length);

        MessageDigest md = MessageDigest.getInstance(macAlgo, PROVIDER);
        byte[] digest = md.digest(mac);

        // TEST
        System.out.println("KS Decrypt=" + Hex.toHexString(dec));
        System.out.println("KS Digest=" + Hex.toHexString(digest));

        return new byte[][]{dec, digest};
    }

    public static String[] generateICXKeystore(String pwd) {

        KeyPair keyPair = null;
        String address = null;
        String id = null;
        byte[] privKey = null;

        try {
            keyPair = PKIUtils.generateKey();
            privKey = PKIUtils.ecPrivateKey2Bytes(keyPair.getPrivate());
            byte[] pubKey = PKIUtils.getPublicKeyFromPrivateKey(PKIUtils.ecPrivateKey2Bytes(keyPair.getPrivate()), true);
            System.out.println("KS PrivateKey=" + Hex.toHexString(privKey));
            System.out.println("KS PublicKey=" + Hex.toHexString(pubKey));
            address = PKIUtils.makeAddressFromPrivateKey(privKey, Constants.KS_COINTYPE_ICX);
            System.out.println("KS address=" + address);

            BigInteger[] sign = PKIUtils.sign(PKIUtils.hash("HI".getBytes(), PKIUtils.ALGORITHM_HASH), privKey);
            byte recoverId = PKIUtils.getRecoveryId(sign, PKIUtils.hash("HI".getBytes(), PKIUtils.ALGORITHM_HASH), pubKey);
            System.out.println("KS recoverId=" + recoverId);

            id = UUID.randomUUID().toString();
            System.out.println("KS id=" + id);
        } catch (Exception e) {
            e.printStackTrace();
        }

        SecureRandom secureRandom = new SecureRandom();
        byte[] salt = new byte[32];
        secureRandom.nextBytes(salt);
        System.out.println("KS salt=" + Hex.toHexString(salt));

        secureRandom = new SecureRandom();
        byte[] iv = new byte[16];
        secureRandom.nextBytes(iv);
        System.out.println("KS iv=" + Hex.toHexString(iv));

        byte[] encKey = pbkdf2(pwd.toCharArray(), PBE_DKLEN, PBKDF2_COUNT, salt);
        System.out.println("KS EncKey=" + Hex.toHexString(encKey));

        String[] cipherTAndMac = null;
        try {
            cipherTAndMac = ksEncrypt(encKey, privKey, iv, salt, PBE_MAC_KECCAK);
            System.out.println("KS cipherText=" + cipherTAndMac[0]);
            System.out.println("KS Mac=" + cipherTAndMac[1]);
        } catch (Exception e) {
            e.printStackTrace();
        }

        JsonObject mKeystore = new JsonObject();
        JsonObject crypto = new JsonObject();
        JsonObject cyrptParam = new JsonObject();
        JsonObject pbkdfParam = new JsonObject();

        cyrptParam.addProperty("iv", Hex.toHexString(iv));

        pbkdfParam.addProperty("dklen", PBE_DKLEN);
        pbkdfParam.addProperty("salt", Hex.toHexString(salt));
        pbkdfParam.addProperty("c", PBKDF2_COUNT);
        pbkdfParam.addProperty("prf", "hmac-sha256");

        crypto.addProperty("ciphertext", cipherTAndMac[0]);
        crypto.add("cipherparams", cyrptParam);
        crypto.addProperty("cipher", "aes-128-ctr");
        crypto.addProperty("kdf", "pbkdf2");
        crypto.add("kdfparams", pbkdfParam);
        crypto.addProperty("mac", cipherTAndMac[1]);

        mKeystore.addProperty("version", 3);
        mKeystore.addProperty("id", id);
        mKeystore.addProperty("address", address);
        mKeystore.add("crypto", crypto);
        mKeystore.addProperty("coinType", Constants.KS_COINTYPE_ICX.toLowerCase());

        System.out.println("ICX KeyStore=" + mKeystore.toString());

        boolean isValid = validateAddress(address, privKey, Constants.KS_COINTYPE_ICX);
        if (isValid) {
            return new String[]{address, Hex.toHexString(privKey), mKeystore.toString()};
        } else {
            return null;
        }
    }

    public static String[] generateEtherKeystore(String pwd) {

        KeyPair keyPair = null;
        String address = null;
        String id = null;
        byte[] privKey = null;

        try {
            keyPair = PKIUtils.generateKey();
            privKey = PKIUtils.ecPrivateKey2Bytes(keyPair.getPrivate());
            byte[] pubKey = PKIUtils.getPublicKeyFromPrivateKey(PKIUtils.ecPrivateKey2Bytes(keyPair.getPrivate()), true);
            System.out.println("KS PrivateKey=" + Hex.toHexString(privKey));
            System.out.println("KS PublicKey=" + Hex.toHexString(pubKey));
            address = PKIUtils.makeAddressFromPrivateKey(privKey, Constants.KS_COINTYPE_ETH);
            System.out.println("KS address=" + address);
            id = UUID.randomUUID().toString();
            System.out.println("KS id=" + id);
        } catch (Exception e) {
            e.printStackTrace();
        }

        SecureRandom secureRandom = new SecureRandom();
        byte[] salt = new byte[32];
        secureRandom.nextBytes(salt);
        System.out.println("KS salt=" + Hex.toHexString(salt));

        secureRandom = new SecureRandom();
        byte[] iv = new byte[16];
        secureRandom.nextBytes(iv);
        System.out.println("KS iv=" + Hex.toHexString(iv));

        byte[] encKey = scrypt(pwd.toCharArray(), PBE_DKLEN, 1024, 8, 1, salt);
        System.out.println("KS EncKey=" + Hex.toHexString(encKey));

        String[] cipherTAndMac = null;
        try {
            cipherTAndMac = ksEncrypt(encKey, PKIUtils.ecPrivateKey2Bytes(keyPair.getPrivate()), iv, salt, PBE_MAC_KECCAK);
            System.out.println("KS cipherText=" + cipherTAndMac[0]);
            System.out.println("KS Mac=" + cipherTAndMac[1]);
        } catch (Exception e) {
            e.printStackTrace();
        }

        JsonObject mKeystore = new JsonObject();
        JsonObject crypto = new JsonObject();
        JsonObject cyrptParam = new JsonObject();
        JsonObject pbkdfParam = new JsonObject();

        cyrptParam.addProperty("iv", Hex.toHexString(iv));

        pbkdfParam.addProperty("dklen", PBE_DKLEN);
        pbkdfParam.addProperty("salt", Hex.toHexString(salt));
        pbkdfParam.addProperty("n", 1024);
        pbkdfParam.addProperty("r", 8);
        pbkdfParam.addProperty("p", 1);
        pbkdfParam.addProperty("prf", "hmac-sha256");

        crypto.addProperty("ciphertext", cipherTAndMac[0]);
        crypto.add("cipherparams", cyrptParam);
        crypto.addProperty("cipher", "aes-128-ctr");
        crypto.addProperty("kdf", "scrypt");
        crypto.add("kdfparams", pbkdfParam);
        crypto.addProperty("mac", cipherTAndMac[1]);

        mKeystore.addProperty("version", 3);
        mKeystore.addProperty("id", id);
        mKeystore.addProperty("address", address);
        mKeystore.add("Crypto", crypto);

        System.out.println("ETH KeyStore=" + mKeystore.toString());

        boolean isValid = validateAddress(address, privKey, Constants.KS_COINTYPE_ETH);
        if (isValid) {
            return new String[]{address, Hex.toHexString(privKey), mKeystore.toString()};
        } else {
            return null;
        }

    }

    public static String[] generateICXKeyStoreByPriv(String pwd, byte[] privKey) {
        String address = null;
        String id = null;

        try {
            address = PKIUtils.makeAddressFromPrivateKey(privKey, Constants.KS_COINTYPE_ICX);
            System.out.println("KS address=" + address);

            id = UUID.randomUUID().toString();
            System.out.println("KS id=" + id);
        } catch (Exception e) {
            e.printStackTrace();
        }

        SecureRandom secureRandom = new SecureRandom();
        byte[] salt = new byte[32];
        secureRandom.nextBytes(salt);
        System.out.println("KS salt=" + Hex.toHexString(salt));

        secureRandom = new SecureRandom();
        byte[] iv = new byte[16];
        secureRandom.nextBytes(iv);
        System.out.println("KS iv=" + Hex.toHexString(iv));

        byte[] encKey = pbkdf2(pwd.toCharArray(), PBE_DKLEN, PBKDF2_COUNT, salt);
        System.out.println("KS EncKey=" + Hex.toHexString(encKey));

        String[] cipherTAndMac = null;
        try {
            cipherTAndMac = ksEncrypt(encKey, privKey, iv, salt, PBE_MAC_KECCAK);
            System.out.println("KS cipherText=" + cipherTAndMac[0]);
            System.out.println("KS Mac=" + cipherTAndMac[1]);
        } catch (Exception e) {
            e.printStackTrace();
        }

        JsonObject mKeystore = new JsonObject();
        JsonObject crypto = new JsonObject();
        JsonObject cyrptParam = new JsonObject();
        JsonObject pbkdfParam = new JsonObject();

        cyrptParam.addProperty("iv", Hex.toHexString(iv));

        pbkdfParam.addProperty("dklen", PBE_DKLEN);
        pbkdfParam.addProperty("salt", Hex.toHexString(salt));
        pbkdfParam.addProperty("c", PBKDF2_COUNT);
        pbkdfParam.addProperty("prf", "hmac-sha256");

        crypto.addProperty("ciphertext", cipherTAndMac[0]);
        crypto.add("cipherparams", cyrptParam);
        crypto.addProperty("cipher", "aes-128-ctr");
        crypto.addProperty("kdf", "pbkdf2");
        crypto.add("kdfparams", pbkdfParam);
        crypto.addProperty("mac", cipherTAndMac[1]);

        mKeystore.addProperty("version", 3);
        mKeystore.addProperty("id", id);
        mKeystore.addProperty("address", address);
        mKeystore.add("crypto", crypto);
        mKeystore.addProperty("coinType", Constants.KS_COINTYPE_ICX.toLowerCase());

        System.out.println("ICX KeyStore=" + mKeystore.toString());

        boolean isValid = validateAddress(address, privKey, Constants.KS_COINTYPE_ICX);
        if (isValid) {
            return new String[]{address, Hex.toHexString(privKey), mKeystore.toString()};
        } else {
            return null;
        }
    }

    public static String[] generateETHKSpbkdf2ByPriv(String pwd, byte[] privKey) {
        KeyPair keyPair = null;
        String address = null;
        String id = null;

        try {
            address = PKIUtils.makeAddressFromPrivateKey(privKey, Constants.KS_COINTYPE_ETH);
            System.out.println("KS address=" + address);

            id = UUID.randomUUID().toString();
            System.out.println("KS id=" + id);
        } catch (Exception e) {
            e.printStackTrace();
        }

        SecureRandom secureRandom = new SecureRandom();
        byte[] salt = new byte[32];
        secureRandom.nextBytes(salt);
        System.out.println("KS salt=" + Hex.toHexString(salt));

        secureRandom = new SecureRandom();
        byte[] iv = new byte[16];
        secureRandom.nextBytes(iv);
        System.out.println("KS iv=" + Hex.toHexString(iv));

        byte[] encKey = pbkdf2(pwd.toCharArray(), PBE_DKLEN, PBKDF2_COUNT, salt);
        System.out.println("KS EncKey=" + Hex.toHexString(encKey));

        String[] cipherTAndMac = null;
        try {
            cipherTAndMac = ksEncrypt(encKey, privKey, iv, salt, PBE_MAC_KECCAK);
            System.out.println("KS cipherText=" + cipherTAndMac[0]);
            System.out.println("KS Mac=" + cipherTAndMac[1]);
        } catch (Exception e) {
            e.printStackTrace();
        }

        JsonObject mKeystore = new JsonObject();
        JsonObject crypto = new JsonObject();
        JsonObject cyrptParam = new JsonObject();
        JsonObject pbkdfParam = new JsonObject();

        cyrptParam.addProperty("iv", Hex.toHexString(iv));

        pbkdfParam.addProperty("dklen", PBE_DKLEN);
        pbkdfParam.addProperty("salt", Hex.toHexString(salt));
        pbkdfParam.addProperty("c", PBKDF2_COUNT);
        pbkdfParam.addProperty("prf", "hmac-sha256");

        crypto.addProperty("ciphertext", cipherTAndMac[0]);
        crypto.add("cipherparams", cyrptParam);
        crypto.addProperty("cipher", "aes-128-ctr");
        crypto.addProperty("kdf", "pbkdf2");
        crypto.add("kdfparams", pbkdfParam);
        crypto.addProperty("mac", cipherTAndMac[1]);

        mKeystore.addProperty("version", 3);
        mKeystore.addProperty("id", id);
        mKeystore.addProperty("address", address);
        mKeystore.add("crypto", crypto);

        System.out.println("ETH KeyStore=" + mKeystore.toString());

        boolean isValid = validateAddress(address, privKey, Constants.KS_COINTYPE_ETH);
        if (isValid) {
            return new String[]{address, Hex.toHexString(privKey), mKeystore.toString()};
        } else {
            return null;
        }
    }

    public static String[] generateETHKeyStoreByPriv(String pwd, byte[] privKey) {
        KeyPair keyPair = null;
        String address = null;
        String id = null;

        try {
            address = PKIUtils.makeAddressFromPrivateKey(privKey, Constants.KS_COINTYPE_ETH);
            System.out.println("KS address=" + address);

            id = UUID.randomUUID().toString();
            System.out.println("KS id=" + id);
        } catch (Exception e) {
            e.printStackTrace();
        }

        SecureRandom secureRandom = new SecureRandom();
        byte[] salt = new byte[32];
        secureRandom.nextBytes(salt);
        System.out.println("KS salt=" + Hex.toHexString(salt));

        secureRandom = new SecureRandom();
        byte[] iv = new byte[16];
        secureRandom.nextBytes(iv);
        System.out.println("KS iv=" + Hex.toHexString(iv));

        byte[] encKey = scrypt(pwd.toCharArray(), PBE_DKLEN, 1024, 8, 1, salt);
        System.out.println("KS EncKey=" + Hex.toHexString(encKey));

        String[] cipherTAndMac = null;
        try {
            cipherTAndMac = ksEncrypt(encKey, privKey, iv, salt, PBE_MAC_KECCAK);
            System.out.println("KS cipherText=" + cipherTAndMac[0]);
            System.out.println("KS Mac=" + cipherTAndMac[1]);
        } catch (Exception e) {
            e.printStackTrace();
        }

        JsonObject mKeystore = new JsonObject();
        JsonObject crypto = new JsonObject();
        JsonObject cyrptParam = new JsonObject();
        JsonObject pbkdfParam = new JsonObject();

        cyrptParam.addProperty("iv", Hex.toHexString(iv));

        pbkdfParam.addProperty("dklen", PBE_DKLEN);
        pbkdfParam.addProperty("salt", Hex.toHexString(salt));
        pbkdfParam.addProperty("n", 1024);
        pbkdfParam.addProperty("r", 8);
        pbkdfParam.addProperty("p", 1);
        pbkdfParam.addProperty("prf", "hmac-sha256");

        crypto.addProperty("ciphertext", cipherTAndMac[0]);
        crypto.add("cipherparams", cyrptParam);
        crypto.addProperty("cipher", "aes-128-ctr");
        crypto.addProperty("kdf", "scrypt");
        crypto.add("kdfparams", pbkdfParam);
        crypto.addProperty("mac", cipherTAndMac[1]);

        mKeystore.addProperty("version", 3);
        mKeystore.addProperty("id", id);
        mKeystore.addProperty("address", address);
        mKeystore.add("Crypto", crypto);

        System.out.println("ETH KeyStore=" + mKeystore.toString());

        boolean isValid = validateAddress(address, privKey, Constants.KS_COINTYPE_ETH);
        if (isValid) {
            return new String[]{address, Hex.toHexString(privKey), mKeystore.toString()};
        } else {
            return null;
        }
    }

    public static String changePassword(String coinType, String id, String address, byte[] privKey, String pwd) {
        if (coinType.equals(Constants.KS_COINTYPE_ICX)) {
            SecureRandom secureRandom = new SecureRandom();
            byte[] salt = new byte[32];
            secureRandom.nextBytes(salt);
            System.out.println("KS salt=" + Hex.toHexString(salt));

            secureRandom = new SecureRandom();
            byte[] iv = new byte[16];
            secureRandom.nextBytes(iv);
            System.out.println("KS iv=" + Hex.toHexString(iv));

            byte[] encKey = pbkdf2(pwd.toCharArray(), PBE_DKLEN, PBKDF2_COUNT, salt);
            System.out.println("KS EncKey=" + Hex.toHexString(encKey));

            String[] cipherTAndMac = null;
            try {
                cipherTAndMac = ksEncrypt(encKey, privKey, iv, salt, PBE_MAC_KECCAK);
                System.out.println("KS cipherText=" + cipherTAndMac[0]);
                System.out.println("KS Mac=" + cipherTAndMac[1]);
            } catch (Exception e) {
                e.printStackTrace();
            }

            JsonObject mKeystore = new JsonObject();
            JsonObject crypto = new JsonObject();
            JsonObject cyrptParam = new JsonObject();
            JsonObject pbkdfParam = new JsonObject();

            cyrptParam.addProperty("iv", Hex.toHexString(iv));

            pbkdfParam.addProperty("dklen", PBE_DKLEN);
            pbkdfParam.addProperty("salt", Hex.toHexString(salt));
            pbkdfParam.addProperty("c", PBKDF2_COUNT);
            pbkdfParam.addProperty("prf", "hmac-sha256");

            crypto.addProperty("ciphertext", cipherTAndMac[0]);
            crypto.add("cipherparams", cyrptParam);
            crypto.addProperty("cipher", "aes-128-ctr");
            crypto.addProperty("kdf", "pbkdf2");
            crypto.add("kdfparams", pbkdfParam);
            crypto.addProperty("mac", cipherTAndMac[1]);

            mKeystore.addProperty("version", Constants.KS_VERSION);
            mKeystore.addProperty("id", id);
            mKeystore.addProperty("address", address);
            mKeystore.add("crypto", crypto);
            mKeystore.addProperty("coinType", Constants.KS_COINTYPE_ICX.toLowerCase());

            System.out.println("ICX KeyStore=" + mKeystore.toString());

            boolean isValid = validateAddress(address, privKey, Constants.KS_COINTYPE_ICX);
            if (isValid) {
                return mKeystore.toString();
            } else {
                return null;
            }
        } else {
            SecureRandom secureRandom = new SecureRandom();
            byte[] salt = new byte[32];
            secureRandom.nextBytes(salt);
            System.out.println("KS salt=" + Hex.toHexString(salt));

            secureRandom = new SecureRandom();
            byte[] iv = new byte[16];
            secureRandom.nextBytes(iv);
            System.out.println("KS iv=" + Hex.toHexString(iv));

            byte[] encKey = scrypt(pwd.toCharArray(), PBE_DKLEN, 1024, 8, 1, salt);
            System.out.println("KS EncKey=" + Hex.toHexString(encKey));

            String[] cipherTAndMac = null;
            try {
                cipherTAndMac = ksEncrypt(encKey, privKey, iv, salt, PBE_MAC_KECCAK);
                System.out.println("KS cipherText=" + cipherTAndMac[0]);
                System.out.println("KS Mac=" + cipherTAndMac[1]);
            } catch (Exception e) {
                e.printStackTrace();
            }

            JsonObject mKeystore = new JsonObject();
            JsonObject crypto = new JsonObject();
            JsonObject cyrptParam = new JsonObject();
            JsonObject pbkdfParam = new JsonObject();

            cyrptParam.addProperty("iv", Hex.toHexString(iv));

            pbkdfParam.addProperty("dklen", PBE_DKLEN);
            pbkdfParam.addProperty("salt", Hex.toHexString(salt));
            pbkdfParam.addProperty("n", 1024);
            pbkdfParam.addProperty("r", 8);
            pbkdfParam.addProperty("p", 1);
            pbkdfParam.addProperty("prf", "hmac-sha256");

            crypto.addProperty("ciphertext", cipherTAndMac[0]);
            crypto.add("cipherparams", cyrptParam);
            crypto.addProperty("cipher", "aes-128-ctr");
            crypto.addProperty("kdf", "scrypt");
            crypto.add("kdfparams", pbkdfParam);
            crypto.addProperty("mac", cipherTAndMac[1]);

            mKeystore.addProperty("version", Constants.KS_VERSION);
            mKeystore.addProperty("id", id);
            mKeystore.addProperty("address", address);
            mKeystore.add("Crypto", crypto);

            System.out.println("ETH KeyStore=" + mKeystore.toString());

            boolean isValid = validateAddress(address, privKey, Constants.KS_COINTYPE_ETH);
            if (isValid) {
                return mKeystore.toString();
            } else {
                return null;
            }
        }
    }

    public static boolean validateAddress(final String address, final byte[] privateKey, String coinType) {
        byte[] recoverPubKey = null;

        boolean equalResult = false;

        final String testStr = "Hello It's me.";

        try {
            byte[] message = PKIUtils.hash(testStr.getBytes(), PKIUtils.ALGORITHM_HASH);
            String signature = PKIUtils.sign(message, Hex.toHexString(privateKey));
            System.out.println("Signature=" + signature);

            recoverPubKey = PKIUtils.verify(message, signature);

            equalResult = checkAddress(address, recoverPubKey, coinType);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (equalResult) {
            return true;
        } else {
            return false;
        }
    }

    private static boolean checkAddress(String address, byte[] publicKey, String coinType) throws NoSuchAlgorithmException, NoSuchProviderException {
        String newAddress = null;

        if (coinType.equals(Constants.KS_COINTYPE_ICX)) {
            newAddress = PKIUtils.makeAddress(publicKey);
        } else {
            newAddress = PKIUtils.makeEtherAddress(publicKey);
        }

        System.out.println("KS validate address");
        System.out.println("KS Address=" + address);
        System.out.println("KS Recover address=" + newAddress);

        return address.equals(newAddress);
    }
}
