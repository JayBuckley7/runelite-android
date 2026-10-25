package net.runelite.mp.plugins;

import java.util.Set;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import static org.objectweb.asm.Opcodes.*;

/** Relocate regex types consistently inside a verified external JAR before D8. */
public final class DesktopRegexTransformer {
    private static final String PLATFORM = "java/util/regex/";
    private static final String DESKTOP = "net/runelite/mp/compat/regex/";
    private static final String STRINGS = "net/runelite/mp/plugins/DesktopRegexStrings";
    private DesktopRegexTransformer() {}

    private static boolean stringRegex(String owner, String name, String descriptor) {
        if (!owner.equals("java/lang/String")) return false;
        switch (name) {
            case "split": return descriptor.equals("(Ljava/lang/String;)[Ljava/lang/String;") || descriptor.equals("(Ljava/lang/String;I)[Ljava/lang/String;");
            case "matches": return descriptor.equals("(Ljava/lang/String;)Z");
            case "splitWithDelimiters": return descriptor.equals("(Ljava/lang/String;I)[Ljava/lang/String;");
            case "replaceFirst":
            case "replaceAll": return descriptor.equals("(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;");
            default: return false;
        }
    }

    public static byte[] rewrite(String entry, byte[] bytes, Set<String> jarClasses) {
        if (!entry.endsWith(".class")) return bytes;
        ClassReader reader = new ClassReader(bytes);
        ClassWriter writer = new ClassWriter(reader, 0);
        boolean[] changed = {false};
        Remapper remapper = new Remapper() {
            @Override public String map(String name) {
                if (name.startsWith(PLATFORM)) {
                    changed[0] = true;
                    return DESKTOP + name.substring(PLATFORM.length());
                }
                return name;
            }

            private void checkBoundary(String owner, String descriptor) {
                if (descriptor.contains("L" + PLATFORM) && !owner.startsWith(PLATFORM) && !jarClasses.contains(owner)) {
                    // A host method still expects Android Pattern/Matcher. Remapping its
                    // descriptor would cause a linkage error; refuse instead of guessing.
                    throw new IllegalArgumentException("Desktop regex crosses host API: " + owner + descriptor);
                }
            }

            @Override public Object mapValue(Object value) {
                if (value instanceof Handle) {
                    Handle handle = (Handle) value;
                    checkBoundary(handle.getOwner(), handle.getDesc());
                    if (handle.getTag() == H_INVOKEVIRTUAL && stringRegex(handle.getOwner(), handle.getName(), handle.getDesc())) {
                        changed[0] = true;
                        return new Handle(H_INVOKESTATIC, STRINGS, handle.getName(), "(Ljava/lang/String;" + handle.getDesc().substring(1), false);
                    }
                }
                return super.mapValue(value);
            }
        };
        ClassVisitor mapped = new ClassRemapper(writer, remapper);
        reader.accept(new ClassVisitor(ASM9, mapped) {
            private void checkBoundary(String owner, String descriptor) {
                if (descriptor.contains("L" + PLATFORM) && !owner.startsWith(PLATFORM) && !jarClasses.contains(owner))
                    throw new IllegalArgumentException("Desktop regex crosses host API: " + owner + descriptor);
            }

            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean itf) {
                        checkBoundary(owner, desc);
                        if (opcode == INVOKEVIRTUAL && stringRegex(owner, method, desc)) {
                            changed[0] = true;
                            super.visitMethodInsn(INVOKESTATIC, STRINGS, method, "(Ljava/lang/String;" + desc.substring(1), false);
                        } else super.visitMethodInsn(opcode, owner, method, desc, itf);
                    }
                    @Override public void visitFieldInsn(int opcode, String owner, String field, String desc) {
                        checkBoundary(owner, desc);
                        super.visitFieldInsn(opcode, owner, field, desc);
                    }
                };
            }
        }, 0);
        return changed[0] ? writer.toByteArray() : bytes;
    }
}
