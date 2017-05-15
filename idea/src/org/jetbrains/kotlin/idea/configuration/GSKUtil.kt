/*
 * Copyright 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.configuration

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.utils.addToStdlib.cast


fun getKotlinScriptDependencySnippet(artifactName: String) =
        "compile(${getKotlinModuleDependencySnippet(artifactName)})"

fun getKotlinModuleDependencySnippet(artifactName: String) =
        "kotlinModule(\"${artifactName.removePrefix("kotlin-")}\", extra[\"$KOTLIN_VERSION_PROPERTY_NAME\"].toString())"

fun KtFile.containsCompileStdLib(): Boolean =
        findScriptInitializer("dependencies")?.getBlock()?.findCompileStdLib() != null

fun KtFile.containsApplyKotlinPlugin(pluginName: String): Boolean =
        findScriptInitializer("apply")?.getBlock()?.findPlugin(pluginName) != null

fun KtBlockExpression.findCompileStdLib() = PsiTreeUtil.getChildrenOfType(this, KtCallExpression::class.java)?.find {
    it.calleeExpression?.text == "compile" && (it.valueArguments.firstOrNull()?.getArgumentExpression()?.isKotlinStdLib() ?: false)
}

fun KtFile.getRepositoriesBlock(): KtBlockExpression? =
        findScriptInitializer("repositories")?.getBlock() ?: addTopLevelBlock("repositories")

fun KtFile.getDependenciesBlock(): KtBlockExpression? =
        findScriptInitializer("dependencies")?.getBlock() ?: addTopLevelBlock("dependencies")

fun KtFile.createApplyBlock(): KtBlockExpression? {
    val apply = psiFactory.createScriptInitializer("apply {\n}")
    val plugins = findScriptInitializer("plugins")
    val addedElement = plugins?.addSibling(apply) ?: addToScriptBlock(apply)
    addedElement?.addNewLinesIfNeeded()
    return (addedElement as? KtScriptInitializer)?.getBlock()
}

fun KtFile.getApplyBlock(): KtBlockExpression? = findScriptInitializer("apply")?.getBlock() ?: createApplyBlock()

private fun KtExpression.isKotlinStdLib(): Boolean = when (this) {
    is KtCallExpression -> calleeExpression?.text == "kotlinModule" &&
                           valueArguments.firstOrNull()?.getArgumentExpression()?.text == "\"stdlib\""
    is KtStringTemplateExpression -> text.startsWith("\"org.jetbrains.kotlin:kotlin-stdlib:")
    else -> false
}

private fun KtBlockExpression.findPlugin(pluginName: String) =
        PsiTreeUtil.getChildrenOfType(this, KtCallExpression::class.java)?.find {
            it.calleeExpression?.text == "plugin" && it.valueArguments.firstOrNull()?.text == "\"$pluginName\""
        }

fun KtBlockExpression.createPluginIfMissing(pluginName: String): KtCallExpression? =
        findPlugin(pluginName) ?: addExpressionIfMissing("plugin(\"$pluginName\")") as? KtCallExpression

fun changeCoroutineConfiguration(buildScriptFile: KtFile, coroutineOption: String): PsiElement? {
    val snippet = "experimental.coroutines = Coroutines.${coroutineOption.toUpperCase()}"
    val kotlinBlock = buildScriptFile.findScriptInitializer("kotlin")?.getBlock() ?:
                      buildScriptFile.addTopLevelBlock("kotlin") ?: return null
    buildScriptFile.addImportIfMissing("org.jetbrains.kotlin.gradle.dsl.Coroutines")
    val statement = kotlinBlock.statements.find { it.text.startsWith("experimental.coroutines") }
    return if (statement != null) {
        statement.replace(buildScriptFile.psiFactory.createExpression(snippet))
    }
    else {
        kotlinBlock.add(buildScriptFile.psiFactory.createExpression(snippet)).apply { addNewLinesIfNeeded() }
    }
}

fun KtFile.changeKotlinTaskParameter(parameterName: String, parameterValue: String, forTests: Boolean): PsiElement? {
    val snippet = "$parameterName = \"$parameterValue\""
    val taskName = if (forTests) "compileTestKotlin" else "compileKotlin"
    val optionsBlock = findScriptInitializer("$taskName.kotlinOptions")?.getBlock()
    return if (optionsBlock != null) {
        val assignment = optionsBlock.statements.find {
            (it as? KtBinaryExpression)?.left?.text == parameterName
        }
        if (assignment != null) {
            assignment.replace(psiFactory.createExpression(snippet))
        }
        else {
            optionsBlock.addExpressionIfMissing(snippet)
        }
    }
    else {
        addImportIfMissing("org.jetbrains.kotlin.gradle.tasks.KotlinCompile")
        addToScriptBlock(psiFactory.createDeclaration("val $taskName: KotlinCompile by tasks"))
        addTopLevelBlock("$taskName.kotlinOptions")?.addExpressionIfMissing(snippet)
    }
}

fun KtFile.getBuildScriptBlock(): KtBlockExpression? =
        findScriptInitializer("buildscript")?.getBlock() ?: addTopLevelBlock("buildscript", true)

fun KtBlockExpression.getRepositoriesBlock(): KtBlockExpression? =
        findBlock("repositories") ?: addBlock("repositories")

fun KtBlockExpression.getDependenciesBlock(): KtBlockExpression? =
        findBlock("dependencies") ?: addBlock("dependencies")

fun KtBlockExpression.addRepositoryIfMissing(version: String): KtCallExpression? {
    val repository = getRepositoryForVersion(version)
    val snippet = when {
        repository != null -> repository.toKotlinRepositorySnippet()
        !isRepositoryConfigured() -> MAVEN_CENTRAL
        else -> return null
    }

    return addExpressionIfMissing(snippet) as? KtCallExpression
}

private fun KtBlockExpression.isRepositoryConfigured(): Boolean {
    return text.contains(MAVEN_CENTRAL) || text.contains(JCENTER)
}

fun KtBlockExpression.addPluginToClassPathIfMissing(): KtCallExpression? =
        addExpressionIfMissing("classpath(${getKotlinModuleDependencySnippet("gradle-plugin")})") as? KtCallExpression

private fun KtFile.findScriptInitializer(startsWith: String): KtScriptInitializer? =
        PsiTreeUtil.findChildrenOfType(this, KtScriptInitializer::class.java).find { it.text.startsWith(startsWith) }

private fun KtBlockExpression.findBlock(name: String): KtBlockExpression? =
        getChildrenOfType<KtCallExpression>().find {
            it.calleeExpression?.text == name &&
            it.valueArguments.singleOrNull()?.getArgumentExpression() is KtLambdaExpression
        }?.getBlock()

private fun KtScriptInitializer.getBlock() =
        PsiTreeUtil.findChildOfType<KtCallExpression>(this, KtCallExpression::class.java)?.getBlock()

private fun KtCallExpression.getBlock() =
        (valueArguments.singleOrNull()?.getArgumentExpression() as? KtLambdaExpression)?.bodyExpression

private fun KtBlockExpression.addBlock(name: String): KtBlockExpression? =
        add(psiFactory.createExpression("$name {\n}"))
                ?.apply { addNewLinesIfNeeded() }
                ?.cast<KtCallExpression>()
                ?.getBlock()

private fun KtFile.addTopLevelBlock(name: String, first: Boolean = false): KtBlockExpression? {
    val scriptInitializer = psiFactory.createScriptInitializer("$name {\n}")
    val addedElement = addToScriptBlock(scriptInitializer, first) as? KtScriptInitializer
    addedElement?.addNewLinesIfNeeded()
    return addedElement?.getBlock()
}

private fun PsiElement.addSibling(element: PsiElement): PsiElement = parent.addAfter(element, this)

private fun PsiElement.addNewLineBefore(lineBreaks: Int = 1) =
        parent.addBefore(psiFactory.createNewLine(lineBreaks), this)

private fun PsiElement.addNewLineAfter(lineBreaks: Int = 1) =
        parent.addAfter(psiFactory.createNewLine(lineBreaks), this)

private fun PsiElement.addNewLinesIfNeeded(lineBreaks: Int = 1) {
    if (prevSibling != null && prevSibling.text.isNotBlank()) {
        addNewLineBefore(lineBreaks)
    }

    if (nextSibling != null && nextSibling.text.isNotBlank()) {
        addNewLineAfter(lineBreaks)
    }
}

private fun KtFile.addToScriptBlock(element: PsiElement, first: Boolean = false) =
        if (first) script?.blockExpression?.addAfter(element, null) else script?.blockExpression?.add(element)

private fun KtFile.addImportIfMissing(path: String): KtImportDirective? =
        importDirectives.find { it.importPath?.pathStr == path } ?:
        importList?.add(psiFactory.createImportDirective(ImportPath.fromString(path))) as KtImportDirective

fun KtBlockExpression.addExpressionIfMissing(expressionText: String, first: Boolean = false): KtExpression? {
    if (text.contains(expressionText)) {
        return null
    }

    val elementToAdd = psiFactory.createExpression(expressionText)
    val addedElement = if (first) {
        addAfter(elementToAdd, null)
    }
    else {
        add(elementToAdd)
    }
    addedElement?.addNewLinesIfNeeded()
    return addedElement as? KtExpression
}

private fun KtPsiFactory.createScriptInitializer(text: String): KtScriptInitializer =
        createFile("dummy.kts", text).script?.blockExpression?.firstChild as KtScriptInitializer

private val PsiElement.psiFactory: KtPsiFactory
    get() = KtPsiFactory(this)


private val MAVEN_CENTRAL = "mavenCentral()"
private val JCENTER = "jcenter()"
private val KOTLIN_VERSION_PROPERTY_NAME = "kotlin_version"