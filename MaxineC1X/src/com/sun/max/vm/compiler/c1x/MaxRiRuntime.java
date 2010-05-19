/*
 * Copyright (c) 2009 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
package com.sun.max.vm.compiler.c1x;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import com.sun.c1x.*;
import com.sun.c1x.target.amd64.*;
import com.sun.c1x.util.*;
import com.sun.cri.ci.*;
import com.sun.cri.ci.CiTargetMethod.*;
import com.sun.cri.ci.CiTargetMethod.Safepoint;
import com.sun.cri.ri.*;
import com.sun.max.annotate.*;
import com.sun.max.asm.*;
import com.sun.max.asm.dis.*;
import com.sun.max.io.*;
import com.sun.max.platform.*;
import com.sun.max.unsafe.*;
import com.sun.max.vm.*;
import com.sun.max.vm.actor.*;
import com.sun.max.vm.actor.holder.*;
import com.sun.max.vm.actor.member.*;
import com.sun.max.vm.bytecode.*;
import com.sun.max.vm.compiler.*;
import com.sun.max.vm.compiler.target.*;
import com.sun.max.vm.runtime.*;
import com.sun.max.vm.thread.*;
import com.sun.max.vm.value.*;

/**
 * The {@code MaxRiRuntime} class implements the runtime interface needed by C1X.
 * This includes access to runtime features such as class and method representations,
 * constant pools, as well as some compiler tuning.
 *
 * @author Ben L. Titzer
 */
public class MaxRiRuntime implements RiRuntime {

    private final C1XCompilerScheme compilerScheme;
    private RiSnippets snippets;

    public MaxRiRuntime(C1XCompilerScheme compilerScheme) {
        this.compilerScheme = compilerScheme;
    }

    private static final CiRegister[] generalParameterRegisters = new CiRegister[]{AMD64.rdi, AMD64.rsi, AMD64.rdx, AMD64.rcx, AMD64.r8, AMD64.r9};
    private static final CiRegister[] xmmParameterRegisters = new CiRegister[]{AMD64.xmm0, AMD64.xmm1, AMD64.xmm2, AMD64.xmm3, AMD64.xmm4, AMD64.xmm5, AMD64.xmm6, AMD64.xmm7};

    /**
     * Gets the constant pool for a specified method.
     * @param method the compiler interface method
     * @return the compiler interface constant pool for the specified method
     */
    public RiConstantPool getConstantPool(RiMethod method) {
        return asClassMethodActor(method, "getConstantPool()").compilee().codeAttribute().constantPool;
    }

    /**
     * Gets the OSR frame for a particular method at a particular bytecode index.
     * @param method the compiler interface method
     * @param bci the bytecode index
     * @return the OSR frame
     */
    public RiOsrFrame getOsrFrame(RiMethod method, int bci) {
        throw FatalError.unimplemented();
    }

    /**
     * Remove once C1X can compile native method stubs.
     */
    public static final boolean CAN_COMPILE_NATIVE_METHODS = "true".equals(System.getenv("C1X_CAN_COMPILE_NATIVE_METHODS"));

    /**
     * Remove once C1X implements the semantics of the ACCESSOR annotation.
     */
    public static final boolean CAN_COMPILE_ACCESSOR_METHODS = false;

    /**
     * Checks whether the runtime requires inlining of the specified method.
     * @param method the method to inline
     * @return {@code true} if the method must be inlined; {@code false}
     * to allow the compiler to use its own heuristics
     */
    public boolean mustInline(RiMethod method) {
        if (!method.isResolved()) {
            return false;
        }
        final ClassMethodActor classMethodActor = asClassMethodActor(method, "mustNotInline()");
        if (classMethodActor.accessor() != null && !CAN_COMPILE_ACCESSOR_METHODS) {
            return false;
        }
        if (classMethodActor.isNative() && !CAN_COMPILE_NATIVE_METHODS) {
            return false;
        }
        return classMethodActor.isInline();
    }

    /**
     * Checks whether the runtime forbids inlining of the specified method.
     * @param method the method to inline
     * @return {@code true} if the runtime forbids inlining of the specified method;
     * {@code false} to allow the compiler to use its own heuristics
     */
    public boolean mustNotInline(RiMethod method) {
        if (!method.isResolved()) {
            return false;
        }
        final ClassMethodActor classMethodActor = asClassMethodActor(method, "mustNotInline()");
        if (classMethodActor.accessor() != null && !CAN_COMPILE_ACCESSOR_METHODS) {
            return true;
        }
        if (classMethodActor.isNative() && !CAN_COMPILE_NATIVE_METHODS) {
            return true;
        }

        return classMethodActor.originalCodeAttribute() == null || classMethodActor.isNeverInline();
    }

    /**
     * Checks whether the runtime forbids compilation of the specified method.
     * @param method the method to compile
     * @return {@code true} if the runtime forbids compilation of the specified method;
     * {@code false} to allow the compiler to compile the method
     */
    public boolean mustNotCompile(RiMethod method) {
        return false;
    }

    ClassMethodActor asClassMethodActor(RiMethod method, String operation) {
        if (method instanceof ClassMethodActor) {
            return (ClassMethodActor) method;
        }
        throw new CiUnresolvedException("invalid RiMethod instance: " + method.getClass());
    }

    public int threadExceptionOffset() {
        return VmThreadLocal.EXCEPTION_OBJECT.offset;
    }

    public int basicObjectLockOffsetInBytes() {
        return Util.nonFatalUnimplemented(0);
    }

    public int sizeofBasicObjectLock() {
        // TODO Auto-generated method stub
        return 0;
    }

    public int codeOffset() {
        return CallEntryPoint.OPTIMIZED_ENTRY_POINT.offset();
    }

    @Override
    public void codePrologue(RiMethod method, OutputStream out) {
        ClassMethodActor callee = asClassMethodActor(method, "codePrologue()");
        AdapterGenerator generator = AdapterGenerator.forCallee(callee, CallEntryPoint.OPTIMIZED_ENTRY_POINT);
        if (generator != null) {
            generator.adapt(callee, out);
        }
    }

    @Override
    public String disassemble(RiMethod method) {
        ClassMethodActor classMethodActor = asClassMethodActor(method, "disassemble()");
        return classMethodActor.format("%f %R %H.%n(%P)") + String.format("%n%s", CodeAttributePrinter.toString(classMethodActor.codeAttribute()));
    }

    public String disassemble(byte[] code) {
        if (MaxineVM.isHosted()) {
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final IndentWriter writer = new IndentWriter(new OutputStreamWriter(byteArrayOutputStream));
            writer.flush();
            final ProcessorKind processorKind = VMConfiguration.target().platform().processorKind;
            final InlineDataDecoder inlineDataDecoder = null;
            final Pointer startAddress = Pointer.fromInt(0);
            final DisassemblyPrinter disassemblyPrinter = new DisassemblyPrinter(false);
            Disassembler.disassemble(byteArrayOutputStream, code, processorKind.instructionSet, processorKind.dataModel.wordWidth, startAddress.toLong(), inlineDataDecoder, disassemblyPrinter);
            return byteArrayOutputStream.toString();
        }
        return "";
    }

    @Override
    public String disassemble(final CiTargetMethod targetMethod) {
        if (MaxineVM.isHosted()) {
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final IndentWriter writer = new IndentWriter(new OutputStreamWriter(byteArrayOutputStream));
            writer.flush();
            final ProcessorKind processorKind = VMConfiguration.target().platform().processorKind;
            final InlineDataDecoder inlineDataDecoder = null;
            final Pointer startAddress = Pointer.fromInt(0);
            final DisassemblyPrinter disassemblyPrinter = new DisassemblyPrinter(false) {
                private String toString(Call call) {
                    if (call.runtimeCall != null) {
                        return "{" + call.runtimeCall.name() + "}";
                    } else if (call.symbol != null) {
                        return "{" + call.symbol + "}";
                    } else if (call.globalStubID != null) {
                        return "{" + call.globalStubID + "}";
                    } else {
                        return "{" + call.method + "}";
                    }
                }
                private String siteInfo(int pcOffset) {
                    for (Call call : targetMethod.directCalls) {
                        if (call.pcOffset == pcOffset) {
                            return toString(call);
                        }
                    }
                    for (Call call : targetMethod.indirectCalls) {
                        if (call.pcOffset == pcOffset) {
                            return toString(call);
                        }
                    }
                    for (Safepoint site : targetMethod.safepoints) {
                        if (site.pcOffset == pcOffset) {
                            return "{safepoint}";
                        }
                    }
                    for (DataPatch site : targetMethod.dataReferences) {
                        if (site.pcOffset == pcOffset) {
                            return "{" + site.data + "}";
                        }
                    }
                    return null;
                }

                @Override
                protected String disassembledObjectString(Disassembler disassembler, DisassembledObject disassembledObject) {
                    final String string = super.disassembledObjectString(disassembler, disassembledObject);

                    String site = siteInfo(disassembledObject.startPosition());
                    if (site != null) {
                        return string + " " + site;
                    }
                    return string;
                }
            };
            byte[] code = Arrays.copyOf(targetMethod.targetCode(), targetMethod.targetCodeSize());
            Disassembler.disassemble(byteArrayOutputStream, code, processorKind.instructionSet, processorKind.dataModel.wordWidth, startAddress.toLong(), inlineDataDecoder, disassemblyPrinter);
            return byteArrayOutputStream.toString();
        }
        return "";
    }

    public Method getFoldingMethod(RiMethod method) {
        if (C1XOptions.CanonicalizeFoldableMethods && method.isResolved()) {
            MethodActor methodActor = (MethodActor) method;
            if (Actor.isDeclaredFoldable(methodActor.flags())) {
                return methodActor.toJava();
            }
        }
        return null;
    }

    static class CachedInvocation {
        public CachedInvocation(Value[] args) {
            this.args = args;
        }
        final Value[] args;
        CiConstant result;
    }

    /**
     * Cache to speed up compile-time folding. This works as an invocation of a {@linkplain FOLD foldable}
     * method is guaranteed to be idempotent with respect its arguments.
     */
    private final HashMap<MethodActor, CachedInvocation> cache = new HashMap<MethodActor, CachedInvocation>();

    @Override
    public CiConstant invoke(RiMethod method, CiMethodInvokeArguments args) {
        if (C1XOptions.CanonicalizeFoldableMethods && method.isResolved()) {
            MethodActor methodActor = (MethodActor) method;
            if (Actor.isDeclaredFoldable(methodActor.flags())) {
                Value[] values;
                int length = methodActor.descriptor().argumentCount(!methodActor.isStatic());
                if (length == 0) {
                    values = Value.NONE;
                } else {
                    values = new Value[length];
                    for (int i = 0; i < length; ++i) {
                        CiConstant arg = args.nextArg();
                        if (arg == null) {
                            return null;
                        }
                        Value value;
                        // Checkstyle: stop
                        switch (arg.kind) {
                            case Boolean: value = BooleanValue.from(arg.asBoolean()); break;
                            case Byte:    value = ByteValue.from((byte) arg.asInt()); break;
                            case Char:    value = CharValue.from((char) arg.asInt()); break;
                            case Double:  value = DoubleValue.from(arg.asDouble()); break;
                            case Float:   value = FloatValue.from(arg.asFloat()); break;
                            case Int:     value = IntValue.from(arg.asInt()); break;
                            case Long:    value = LongValue.from(arg.asLong()); break;
                            case Object:  value = ReferenceValue.from(arg.asObject()); break;
                            case Short:   value = ShortValue.from((short) arg.asInt()); break;
                            case Word:    value = WordValue.from(Address.fromLong(arg.asLong())); break;
                            default: throw new IllegalArgumentException();
                        }
                        // Checkstyle: resume
                        values[i] = value;
                    }
                }

                CachedInvocation cachedInvocation = null;
                if (!MaxineVM.isHosted()) {
                    synchronized (cache) {
                        cachedInvocation = cache.get(methodActor);
                        if (cachedInvocation != null) {
                            if (Arrays.equals(values, cachedInvocation.args)) {
                                return cachedInvocation.result;
                            }
                        } else {
                            cachedInvocation = new CachedInvocation(values);
                            cache.put(methodActor, cachedInvocation);
                        }
                    }
                }

                try {
                    // attempt to invoke the method
                    CiConstant result = methodActor.invoke(values).asCiConstant();
                    // set the result of this instruction to be the result of invocation
                    C1XMetrics.MethodsFolded++;

                    if (!MaxineVM.isHosted()) {
                        cachedInvocation.result = result;
                    }

                    return result;
                    // note that for void, we will have a void constant with value null
                } catch (IllegalAccessException e) {
                    // folding failed; too bad
                } catch (InvocationTargetException e) {
                    // folding failed; too bad
                } catch (ExceptionInInitializerError e) {
                    // folding failed; too bad
                }
                return null;
            }
        }
        return null;
    }

    public Object registerTargetMethod(CiTargetMethod ciTargetMethod, String name) {
        return new C1XTargetMethod(name, ciTargetMethod);
    }

    public RiType getRiType(Class<?> javaClass) {
        return ClassActor.fromJava(javaClass);
    }

    public RiMethod getRiMethod(Method method) {
        return MethodActor.fromJava(method);
    }

    public RiMethod getRiMethod(Constructor< ? > constructor) {
        return MethodActor.fromJavaConstructor(constructor);
    }

    public RiField getRiField(Field field) {
        return FieldActor.fromJava(field);
    }

    public RiSnippets getSnippets() {
        if (snippets == null) {
            snippets = new MaxRiSnippets(this);
        }
        return snippets;
    }
}