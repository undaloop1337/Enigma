package cuchaz.enigma.analysis.index;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import cuchaz.enigma.analysis.ReferenceTargetType;
import cuchaz.enigma.translation.representation.AccessFlags;
import cuchaz.enigma.translation.representation.Lambda;
import cuchaz.enigma.translation.representation.MethodDescriptor;
import cuchaz.enigma.translation.representation.Signature;
import cuchaz.enigma.translation.representation.entry.ClassEntry;
import cuchaz.enigma.translation.representation.entry.FieldEntry;
import cuchaz.enigma.translation.representation.entry.MethodDefEntry;
import cuchaz.enigma.translation.representation.entry.MethodEntry;
import cuchaz.enigma.translation.representation.entry.ParentedEntry;

public class IndexReferenceVisitor extends ClassVisitor {
	private final JarIndexer indexer;
	private ClassEntry classEntry;
	private String className;

	public IndexReferenceVisitor(JarIndexer indexer, int api) {
		super(api);
		this.indexer = indexer;
	}

	@Override
	public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
		classEntry = new ClassEntry(name);
		className = name;
	}

	@Override
	public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
		MethodDefEntry entry = new MethodDefEntry(classEntry, name, new MethodDescriptor(desc), Signature.createSignature(signature), new AccessFlags(access));
		return new IndexReferenceMethodVisitor(api, entry, indexer);
	}

	private static class IndexReferenceMethodVisitor extends MethodVisitor {
		private final MethodDefEntry callerEntry;
		private final JarIndexer indexer;

		IndexReferenceMethodVisitor(int api, MethodDefEntry callerEntry, JarIndexer indexer) {
			super(api);
			this.callerEntry = callerEntry;
			this.indexer = indexer;
		}

		@Override
		public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
			indexer.indexFieldReference(callerEntry, FieldEntry.parse(owner, name, descriptor), ReferenceTargetType.none());
		}

		@Override
		public void visitLdcInsn(Object value) {
			if (value instanceof Type type) {
				indexType(type);
			}
		}

		@Override
		public void visitTypeInsn(int opcode, String type) {
			if (opcode == Opcodes.NEW || opcode == Opcodes.ANEWARRAY || opcode == Opcodes.INSTANCEOF || opcode == Opcodes.CHECKCAST) {
				indexType(Type.getObjectType(type));
			}
		}

		@Override
		public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
			indexer.indexMethodReference(callerEntry, MethodEntry.parse(owner, name, descriptor), ReferenceTargetType.none());
			indexMethodDescriptor(descriptor);
		}

		@Override
		public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
			indexMethodDescriptor(descriptor);

			if ("java/lang/invoke/LambdaMetafactory".equals(bootstrapMethodHandle.getOwner()) && ("metafactory".equals(bootstrapMethodHandle.getName()) || "altMetafactory".equals(bootstrapMethodHandle.getName()))) {
				Type samMethodType = (Type) bootstrapMethodArguments[0];
				Handle implMethod = (Handle) bootstrapMethodArguments[1];
				Type instantiatedMethodType = (Type) bootstrapMethodArguments[2];
				indexer.indexLambda(callerEntry, new Lambda(name, new MethodDescriptor(descriptor), new MethodDescriptor(samMethodType.getDescriptor()), getHandleEntry(implMethod), new MethodDescriptor(instantiatedMethodType.getDescriptor())), ReferenceTargetType.none());
			}
		}

		@Override
		public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
			indexType(Type.getType(descriptor));
		}

		private void indexMethodDescriptor(String descriptor) {
			Type methodType = Type.getMethodType(descriptor);

			for (Type argumentType : methodType.getArgumentTypes()) {
				indexType(argumentType);
			}

			indexType(methodType.getReturnType());
		}

		private void indexType(Type type) {
			if (type.getSort() == Type.ARRAY) {
				indexType(type.getElementType());
			} else if (type.getSort() == Type.OBJECT) {
				indexer.indexClassReference(callerEntry, ClassEntry.parse(type.getInternalName()), ReferenceTargetType.none());
			}
		}

		private static ParentedEntry<?> getHandleEntry(Handle handle) {
			return switch (handle.getTag()) {
			case Opcodes.H_GETFIELD, Opcodes.H_GETSTATIC, Opcodes.H_PUTFIELD, Opcodes.H_PUTSTATIC ->
					FieldEntry.parse(handle.getOwner(), handle.getName(), handle.getDesc());
			case Opcodes.H_INVOKEINTERFACE, Opcodes.H_INVOKESPECIAL, Opcodes.H_INVOKESTATIC,
				Opcodes.H_INVOKEVIRTUAL, Opcodes.H_NEWINVOKESPECIAL ->
					MethodEntry.parse(handle.getOwner(), handle.getName(), handle.getDesc());
			default -> throw new RuntimeException("Invalid handle tag " + handle.getTag());
			};
		}
	}
}
