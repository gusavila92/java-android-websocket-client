package dev.gustavoavila.websocketclient.common;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Utility class
 * 
 * @author Gustavo Avila
 *
 */
public class Utils {
	/**
	 * Converts the int value passed as parameter to a 2 byte array
	 * 
	 * @param value
	 * @return
	 */
	public static byte[] to2ByteArray(int value) {
		return new byte[] { (byte) (value >>> 8), (byte) value };
	}

	/**
	 * Converts the int value passed as parameter to a 8 byte array.
	 * Even though the specification allows payloads with sizes greater than 32 bits,
	 * Java only allows integers with 32 bit size, so the first 4 bytes will be zeroes.
	 * 
	 * @param value
	 * @return
	 */
	public static byte[] to8ByteArray(int value) {
		return new byte[] { 0, 0, 0, 0,
				(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value };
	}

	/**
	 * Converts the byte array passed as parameter to an integer
	 * 
	 * @param bytes
	 * @return
	 */
	public static int fromByteArray(byte[] bytes) {
		return bytes[0] << 24 | (bytes[1] & 0xFF) << 16 | (bytes[2] & 0xFF) << 8 | (bytes[3] & 0xFF);
	}

	/**
	 * Encode data to base 64.
	 * It checks if the VM is Dalvik (Android) and uses reflection to call the right classes
	 *
	 * @param data Data to be encoded
	 * @return The encoded data
	 */
	public static String encodeToBase64String(byte[] data) {
		String vmName = System.getProperties().getProperty("java.vm.name");

		try {
			if (vmName.equals("Dalvik")) {
				Method encodeToString = Class.forName("android.util.Base64").getMethod("encodeToString", byte[].class, int.class);;
				return (String) encodeToString.invoke(null, data, 2);
			} else {
				Method encoderMethod = Class.forName("java.util.Base64").getMethod("getEncoder");
				Object encoder = encoderMethod.invoke(null);
				Method encodeToString = encoder.getClass().getMethod("encodeToString", byte[].class);
				return (String) encodeToString.invoke(encoder, data);
			}
		} catch (ClassNotFoundException e) {
			throw new RuntimeException("Base64 class not found");
		} catch (NoSuchMethodException e) {
			throw new RuntimeException("Base64 class not found");
		} catch (IllegalAccessException e) {
			throw new RuntimeException("Base64 class not found");
		} catch (InvocationTargetException e) {
			throw new RuntimeException("Base64 class not found");
		}
	}
}
