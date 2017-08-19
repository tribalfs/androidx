package org.jetbrains.kotlin.r4a

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.codegen.ClassBuilder
import org.jetbrains.kotlin.codegen.ClassBuilderFactory
import org.jetbrains.kotlin.codegen.DelegatingClassBuilder
import org.jetbrains.kotlin.codegen.extensions.ClassBuilderInterceptorExtension
import org.jetbrains.kotlin.diagnostics.DiagnosticSink
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.jvm.diagnostics.JvmDeclarationOrigin
import org.jetbrains.org.objectweb.asm.*

class ReflectiveFragmentInjectorInterceptorExtension : ClassBuilderInterceptorExtension {

    override fun interceptClassBuilderFactory(
            interceptedFactory: ClassBuilderFactory,
            bindingContext: BindingContext,
            diagnostics: DiagnosticSink
    ): ClassBuilderFactory {
        return AndroidOnDestroyClassBuilderFactory(interceptedFactory, bindingContext)
    }

    private inner class AndroidOnDestroyClassBuilderFactory(
            private val delegateFactory: ClassBuilderFactory,
            val bindingContext: BindingContext
    ) : ClassBuilderFactory {

        override fun newClassBuilder(origin: JvmDeclarationOrigin): ClassBuilder {
            return AndroidOnDestroyCollectorClassBuilder(delegateFactory.newClassBuilder(origin), bindingContext)
        }

        override fun getClassBuilderMode() = delegateFactory.classBuilderMode

        override fun asText(builder: ClassBuilder?): String? {
            return delegateFactory.asText((builder as AndroidOnDestroyCollectorClassBuilder).delegateClassBuilder)
        }

        override fun asBytes(builder: ClassBuilder?): ByteArray? {
            return delegateFactory.asBytes((builder as AndroidOnDestroyCollectorClassBuilder).delegateClassBuilder)
        }

        override fun close() {
            delegateFactory.close()
        }
    }

    private inner class AndroidOnDestroyCollectorClassBuilder(
            internal val delegateClassBuilder: ClassBuilder,
            val bindingContext: BindingContext
    ) : DelegatingClassBuilder() {
        private var currentClass: KtClass? = null
        private var currentClassName: String? = null

        override fun getDelegate() = delegateClassBuilder

        override fun defineClass(
                origin: PsiElement?,
                version: Int,
                access: Int,
                name: String,
                signature: String?,
                superName: String,
                interfaces: Array<out String>
        ) {
            if (origin is KtClass) {
                currentClass = origin
                currentClassName = name
            }
            super.defineClass(origin, version, access, name, signature, superName, interfaces)
        }

        override fun newMethod(
                origin: JvmDeclarationOrigin,
                access: Int,
                name: String,
                desc: String,
                signature: String?,
                exceptions: Array<out String>?
        ): MethodVisitor {
            return object : MethodVisitor(Opcodes.ASM5, super.newMethod(origin, access, name, desc, signature, exceptions)) {

                override fun visitMethodInsn(opcode: Int, owner: String?, name: String?, desc: String?, itf: Boolean) {
                    if("com/google/r4a/MarkupFragment".equals(owner) && "unaryPlus".equals(name) && "()V".equals(desc)) {
                        super.visitVarInsn(Opcodes.ALOAD, 1);
                        super.visitTypeInsn(Opcodes.CHECKCAST, "com/google/r4a/MarkupBuilder");
                        super.visitMethodInsn(opcode, owner, "write", "(Lcom/google/r4a/MarkupBuilder;)V", itf)
                        return
                    }
                    super.visitMethodInsn(opcode, owner, name, desc, itf)
                }

/*                override fun visitInsn(opcode: Int) {
                    if (opcode == Opcodes.RETURN) {
                        generateClearCacheMethodCall()
                    }

                    super.visitInsn(opcode)
                }

                private fun generateClearCacheMethodCall() {
                    if (name != ON_DESTROY_METHOD_NAME || currentClass == null) return
                    if (Type.getArgumentTypes(desc).isNotEmpty()) return
                    if (Type.getReturnType(desc) != Type.VOID_TYPE) return

                    val containerType = currentClassName?.let { Type.getObjectType(it) } ?: return

                    val container = bindingContext.get(BindingContext.CLASS, currentClass) ?: return
                    val entityOptions = ContainerOptionsProxy.create(container)
                    if (!entityOptions.containerType.isFragment || !entityOptions.cache.hasCache) return

                    val iv = InstructionAdapter(this)
                    iv.load(0, containerType)
                    iv.invokevirtual(currentClassName, CLEAR_CACHE_METHOD_NAME, "()V", false)
                }
  */          }
        }
    }

}