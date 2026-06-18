package cuchaz.enigma.command.mcp.tools;

import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import cuchaz.enigma.EnigmaProject;
import cuchaz.enigma.command.mcp.McpException;
import cuchaz.enigma.translation.representation.entry.ClassEntry;

public class CryptoAnalysisTool extends BaseTool {
	public CryptoAnalysisTool(EnigmaProject project) {
		super(project);
	}

	@Override
	public String name() {
		return "crypto_analysis";
	}

	@Override
	public String description() {
		return "Find cryptographic API usage: Cipher, MessageDigest, SecretKey, Mac, Signature. Reports algorithms, key sizes, and flags weak configurations.";
	}

	@Override
	public JsonObject inputSchema() {
		JsonObject schema = super.inputSchema();
		JsonObject properties = schema.getAsJsonObject("properties");
		properties.add("className", stringProperty("Optional class to scope."));
		properties.add("limit", integerProperty("Maximum results."));
		return schema;
	}

	@Override
	public JsonObject execute(JsonObject arguments) throws McpException {
		String className = getString(arguments, "className");
		int limit = limit(arguments, 100);
		JsonArray results = new JsonArray();

		for (ClassEntry classEntry : project.getJarIndex().getEntryIndex().getClasses()) {
			if (results.size() >= limit) break;
			if (className != null && !classEntry.getFullName().equals(className)) continue;

			ClassNode node = project.getClassProvider().get(classEntry.getFullName());
			if (node == null) continue;

			for (MethodNode method : node.methods) {
				if (results.size() >= limit) break;

				String lastString = null;
				for (AbstractInsnNode insn : method.instructions) {
					if (results.size() >= limit) break;

					if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String s) {
						lastString = s;
					}

					if (insn instanceof MethodInsnNode min) {
						String cryptoKind = getCryptoKind(min);
						if (cryptoKind != null) {
							JsonObject item = new JsonObject();
							item.addProperty("class", classEntry.getFullName());
							item.addProperty("method", method.name);
							item.addProperty("methodDesc", method.desc);
							item.addProperty("cryptoKind", cryptoKind);
							item.addProperty("api", min.owner + "." + min.name);
							if (lastString != null) {
								item.addProperty("algorithm", lastString);
								String warning = checkWeakCrypto(lastString);
								if (warning != null) {
									item.addProperty("warning", warning);
								}
							}
							results.add(item);
						}
					}
				}
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("count", results.size());
		result.add("usages", results);
		return result;
	}

	private static String getCryptoKind(MethodInsnNode min) {
		String owner = min.owner;
		String name = min.name;
		if (name.equals("getInstance")) {
			if (owner.equals("javax/crypto/Cipher")) return "cipher";
			if (owner.equals("java/security/MessageDigest")) return "digest";
			if (owner.equals("javax/crypto/KeyGenerator")) return "key_generation";
			if (owner.equals("java/security/Signature")) return "signature";
			if (owner.equals("javax/crypto/Mac")) return "mac";
			if (owner.equals("java/security/KeyPairGenerator")) return "keypair_generation";
			if (owner.equals("javax/crypto/SecretKeyFactory")) return "secret_key_factory";
			if (owner.equals("java/security/SecureRandom")) return "secure_random";
		}
		if (owner.equals("javax/crypto/spec/SecretKeySpec") && name.equals("<init>")) return "secret_key_spec";
		if (owner.equals("javax/crypto/spec/IvParameterSpec") && name.equals("<init>")) return "iv_spec";
		return null;
	}

	private static String checkWeakCrypto(String algorithm) {
		if (algorithm == null) return null;
		String upper = algorithm.toUpperCase(Locale.ROOT);
		if (upper.contains("DES") && !upper.contains("3DES") && !upper.contains("DESEDE")) return "WEAK: DES is insecure";
		if (upper.contains("RC4") || upper.contains("ARCFOUR")) return "WEAK: RC4 is broken";
		if (upper.equals("MD5")) return "WEAK: MD5 for security purposes is broken";
		if (upper.equals("SHA1") || upper.equals("SHA-1")) return "CAUTION: SHA-1 deprecated for signatures";
		if (upper.contains("ECB")) return "WEAK: ECB mode leaks patterns";
		if (upper.contains("/NONE/")) return "WEAK: No padding may be insecure";
		return null;
	}
}
