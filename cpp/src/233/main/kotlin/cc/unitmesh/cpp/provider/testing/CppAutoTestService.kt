package cc.unitmesh.cpp.provider.testing

import cc.unitmesh.cpp.util.CppContextPrettify
import cc.unitmesh.devti.context.ClassContext
import cc.unitmesh.devti.provider.AutoTestService
import cc.unitmesh.devti.provider.context.TestFileContext
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.configurations.RunProfile
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.jetbrains.cidr.cpp.execution.testing.tcatch.CMakeCatchTestRunConfigurationType
import com.jetbrains.cidr.lang.OCLanguage
import com.jetbrains.cidr.lang.psi.OCFile
import com.jetbrains.cidr.lang.psi.OCFunctionDeclaration
import java.io.File

class CppAutoTestService : AutoTestService() {
    override fun runConfigurationClass(project: Project): Class<out RunProfile>? = null
    override fun psiFileClass(project: Project): Class<out PsiElement> = OCFile::class.java

    override fun isApplicable(element: PsiElement): Boolean = element.language is OCLanguage

    override fun createConfiguration(project: Project, virtualFile: VirtualFile): RunConfiguration? {
        val cmakeLists = File(project.basePath, "CMakeLists.txt")
        if (!cmakeLists.exists()) {
            return null
        }

        val cmakelist = cmakeLists.readText()
        if (!cmakelist.contains("catch")) {
            return null
        }

        val factory = CMakeCatchTestRunConfigurationType.getInstance().factory
        val settings = CppTestConfiguration.createConfiguration(project, virtualFile, factory).firstOrNull()
        return settings?.configuration
    }

    override fun findOrCreateTestFile(sourceFile: PsiFile, project: Project, element: PsiElement): TestFileContext? {
        // 1. check project root test folder, if not exist, create it
        val baseDir = project.guessProjectDir() ?: return null

        val sourceVirtualFile = sourceFile.virtualFile

        val testFilePath = getTestFilePath(sourceVirtualFile)
        val testFile = WriteAction.computeAndWait<VirtualFile?, Throwable> {
            baseDir.findOrCreateChildData(this, testFilePath)
        } ?: return null

        val currentClass = when (element) {
            is OCFunctionDeclaration -> {
                CppContextPrettify.printParentStructure(element)
            }

            else -> null
        }

        val relatedClasses = lookupRelevantClass(project, element)

        return TestFileContext(
            true,
            testFile,
            relatedClasses,
            "",
            sourceFile.language,
            currentClass,
            emptyList()
        )
    }

    private fun getTestFilePath(file: VirtualFile) = file.nameWithoutExtension + "_test" + "." + file.extension

    override fun lookupRelevantClass(project: Project, element: PsiElement): List<ClassContext> {
        return listOf()
    }
}
