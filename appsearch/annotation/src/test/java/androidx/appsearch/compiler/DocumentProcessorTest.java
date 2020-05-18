/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.appsearch.compiler;

import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import com.google.common.truth.Truth;
import com.google.testing.compile.Compilation;
import com.google.testing.compile.CompilationSubject;
import com.google.testing.compile.Compiler;
import com.google.testing.compile.JavaFileObjects;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestName;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;

import javax.tools.JavaFileObject;

public class DocumentProcessorTest {
    private static final Logger LOG = Logger.getLogger(DocumentProcessor.class.getSimpleName());

    @Rule
    public TemporaryFolder mTemporaryFolder = new TemporaryFolder();
    @Rule
    public TestName mTestName = new TestName();

    private File mGenFilesDir;

    @Before
    public void setUp() throws IOException {
        mGenFilesDir = mTemporaryFolder.newFolder("genFilesDir");
    }

    @Test
    public void testNonClass() {
        Compilation compilation = compile(
                "@AppSearchDocument\n"
                        + "public interface Gift {}\n");
        CompilationSubject.assertThat(compilation).hadErrorContaining(
                "annotation on something other than a class");
    }

    @Test
    public void testPrivate() {
        Compilation compilation = compile(
                "Wrapper",
                "public class Wrapper {\n"
                        + "@AppSearchDocument\n"
                        + "private class Gift {}\n"
                        + "}  // Wrapper\n"
        );
        CompilationSubject.assertThat(compilation).hadErrorContaining(
                "annotated class is private");
    }

    @Test
    public void testNoUri() {
        Compilation compilation = compile(
                "@AppSearchDocument\n"
                        + "public class Gift {}\n");
        CompilationSubject.assertThat(compilation).hadErrorContaining(
                "must have exactly one field annotated with @Uri");
    }

    @Test
    public void testManyUri() {
        Compilation compilation = compile(
                "@AppSearchDocument\n"
                        + "public class Gift {\n"
                        + "  @AppSearchDocument.Uri String uri1;\n"
                        + "  @AppSearchDocument.Uri String uri2;\n"
                        + "}\n");
        CompilationSubject.assertThat(compilation).hadErrorContaining(
                "contains multiple fields annotated @Uri");
    }

    @Test
    public void testManyCreationTimestamp() {
        Compilation compilation = compile(
                "@AppSearchDocument\n"
                        + "public class Gift {\n"
                        + "  @AppSearchDocument.Uri String uri;\n"
                        + "  @AppSearchDocument.CreationTimestampMillis long ts1;\n"
                        + "  @AppSearchDocument.CreationTimestampMillis long ts2;\n"
                        + "}\n");
        CompilationSubject.assertThat(compilation).hadErrorContaining(
                "contains multiple fields annotated @CreationTimestampMillis");
    }

    @Test
    public void testManyTtlMillis() {
        Compilation compilation = compile(
                "@AppSearchDocument\n"
                        + "public class Gift {\n"
                        + "  @AppSearchDocument.Uri String uri;\n"
                        + "  @AppSearchDocument.TtlMillis long ts1;\n"
                        + "  @AppSearchDocument.TtlMillis long ts2;\n"
                        + "}\n");
        CompilationSubject.assertThat(compilation).hadErrorContaining(
                "contains multiple fields annotated @TtlMillis");
    }

    @Test
    public void testManyScore() {
        Compilation compilation = compile(
                "@AppSearchDocument\n"
                        + "public class Gift {\n"
                        + "  @AppSearchDocument.Uri String uri;\n"
                        + "  @AppSearchDocument.Score int score1;\n"
                        + "  @AppSearchDocument.Score int score2;\n"
                        + "}\n");
        CompilationSubject.assertThat(compilation).hadErrorContaining(
                "contains multiple fields annotated @Score");
    }

    @Test
    public void testPropertyOnField() {
        Compilation compilation = compile(
                "@AppSearchDocument\n"
                        + "public class Gift {\n"
                        + "  @AppSearchDocument.Uri String uri;\n"
                        + "  @AppSearchDocument.Property private int getPrice() { return 0; }\n"
                        + "}\n");
        CompilationSubject.assertThat(compilation).hadErrorContaining(
                "annotation type not applicable to this kind of declaration");
    }

    @Test
    public void testCantRead_NoGetter() {
        Compilation compilation = compile(
                "@AppSearchDocument\n"
                        + "public class Gift {\n"
                        + "  @AppSearchDocument.Uri String uri;\n"
                        + "  @AppSearchDocument.Property private int price;\n"
                        + "}\n");
        CompilationSubject.assertThat(compilation).hadErrorContaining(
                "Field cannot be read: it is private and we failed to find a suitable getter "
                        + "named \"getPrice\"");
    }

    @Test
    public void testCantRead_PrivateGetter() {
        Compilation compilation = compile(
                "@AppSearchDocument\n"
                        + "public class Gift {\n"
                        + "  @AppSearchDocument.Uri String uri;\n"
                        + "  @AppSearchDocument.Property private int price;\n"
                        + "  private int getPrice() { return 0; }\n"
                        + "}\n");
        CompilationSubject.assertThat(compilation).hadErrorContaining(
                "Field cannot be read: it is private and we failed to find a suitable getter "
                        + "named \"getPrice\"");
        CompilationSubject.assertThat(compilation).hadWarningContaining(
                "Getter cannot be used: private visibility");
    }

    @Test
    public void testCantRead_WrongParamGetter() {
        Compilation compilation = compile(
                "@AppSearchDocument\n"
                        + "public class Gift {\n"
                        + "  @AppSearchDocument.Uri String uri;\n"
                        + "  @AppSearchDocument.Property private int price;\n"
                        + "  int getPrice(int n) { return 0; }\n"
                        + "}\n");
        CompilationSubject.assertThat(compilation).hadErrorContaining(
                "Field cannot be read: it is private and we failed to find a suitable getter "
                        + "named \"getPrice\"");
        CompilationSubject.assertThat(compilation).hadWarningContaining(
                "Getter cannot be used: should take no parameters");
    }

    @Test
    public void testRead_MultipleGetters() {
        Compilation compilation = compile(
                "@AppSearchDocument\n"
                        + "public class Gift {\n"
                        + "  @AppSearchDocument.Uri String uri;\n"
                        + "  @AppSearchDocument.Property private int price;\n"
                        + "  int getPrice(int n) { return 0; }\n"
                        + "  int getPrice() { return 0; }\n"
                        + "  void setPrice(int n) {}\n"
                        + "}\n");
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    public void testCantWrite_NoSetter() {
        Compilation compilation = compile(
                "@AppSearchDocument\n"
                        + "public class Gift {\n"
                        + "  @AppSearchDocument.Uri String uri;\n"
                        + "  @AppSearchDocument.Property private int price;\n"
                        + "  int getPrice() { return price; }\n"
                        + "}\n");
        CompilationSubject.assertThat(compilation).hadErrorContaining(
                "Failed to find any suitable constructors to build this class");
        CompilationSubject.assertThat(compilation).hadWarningContainingMatch(
                "Field cannot be written .* failed to find a suitable setter named \"setPrice\"");
        CompilationSubject.assertThat(compilation).hadWarningContaining(
                "Cannot use this constructor to construct the class: This constructor doesn't have "
                        + "parameters for the following fields: [price]");
    }

    @Test
    public void testCantWrite_PrivateSetter() {
        Compilation compilation = compile(
                "@AppSearchDocument\n"
                        + "public class Gift {\n"
                        + "  @AppSearchDocument.Uri String uri;\n"
                        + "  @AppSearchDocument.Property private int price;\n"
                        + "  int getPrice() { return price; }\n"
                        + "  private void setPrice(int n) {}\n"
                        + "}\n");
        CompilationSubject.assertThat(compilation).hadErrorContaining(
                "Failed to find any suitable constructors to build this class");
        CompilationSubject.assertThat(compilation).hadWarningContainingMatch(
                "Field cannot be written .* failed to find a suitable setter named \"setPrice\"");
        CompilationSubject.assertThat(compilation).hadWarningContaining(
                "Setter cannot be used: private visibility");
        CompilationSubject.assertThat(compilation).hadWarningContaining(
                "Cannot use this constructor to construct the class: This constructor doesn't have "
                        + "parameters for the following fields: [price]");
    }

    @Test
    public void testCantWrite_WrongParamSetter() {
        Compilation compilation = compile(
                "@AppSearchDocument\n"
                        + "public class Gift {\n"
                        + "  @AppSearchDocument.Uri String uri;\n"
                        + "  @AppSearchDocument.Property private int price;\n"
                        + "  int getPrice() { return price; }\n"
                        + "  void setPrice() {}\n"
                        + "}\n");
        CompilationSubject.assertThat(compilation).hadErrorContaining(
                "Failed to find any suitable constructors to build this class");
        CompilationSubject.assertThat(compilation).hadWarningContainingMatch(
                "Field cannot be written .* failed to find a suitable setter named \"setPrice\"");
        CompilationSubject.assertThat(compilation).hadWarningContaining(
                "Setter cannot be used: takes 0 parameters instead of 1");
        CompilationSubject.assertThat(compilation).hadWarningContaining(
                "Cannot use this constructor to construct the class: This constructor doesn't have "
                        + "parameters for the following fields: [price]");
    }

    @Test
    public void testWrite_MultipleSetters() {
        Compilation compilation = compile(
                "@AppSearchDocument\n"
                        + "public class Gift {\n"
                        + "  @AppSearchDocument.Uri String uri;\n"
                        + "  @AppSearchDocument.Property private int price;\n"
                        + "  int getPrice() { return price; }\n"
                        + "  void setPrice() {}\n"
                        + "  void setPrice(int n) {}\n"
                        + "}\n");
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
    }

    @Test
    public void testWrite_PrivateConstructor() {
        Compilation compilation = compile(
                "@AppSearchDocument\n"
                        + "public class Gift {\n"
                        + "  private Gift() {}\n"
                        + "  @AppSearchDocument.Uri String uri;\n"
                        + "  @AppSearchDocument.Property int price;\n"
                        + "}\n");
        CompilationSubject.assertThat(compilation).hadErrorContaining(
                "Failed to find any suitable constructors to build this class");
        CompilationSubject.assertThat(compilation).hadWarningContaining(
                "Constructor is private");
    }

    @Test
    public void testWrite_ConstructorMissingParams() {
        Compilation compilation = compile(
                "@AppSearchDocument\n"
                        + "public class Gift {\n"
                        + "  Gift(int price) {}\n"
                        + "  @AppSearchDocument.Uri final String uri;\n"
                        + "  @AppSearchDocument.Property int price;\n"
                        + "}\n");
        CompilationSubject.assertThat(compilation).hadErrorContaining(
                "Failed to find any suitable constructors to build this class");
        CompilationSubject.assertThat(compilation).hadWarningContaining(
                "doesn't have parameters for the following fields: [uri]");
    }

    @Test
    public void testWrite_ConstructorExtraParams() {
        Compilation compilation = compile(
                "@AppSearchDocument\n"
                        + "public class Gift {\n"
                        + "  Gift(int price, String uri, int unknownParam) {\n"
                        + "    this.uri = uri;\n"
                        + "    this.price = price;\n"
                        + "  }\n"
                        + "  @AppSearchDocument.Uri final String uri;\n"
                        + "  @AppSearchDocument.Property int price;\n"
                        + "}\n");
        CompilationSubject.assertThat(compilation).hadErrorContaining(
                "Failed to find any suitable constructors to build this class");
        CompilationSubject.assertThat(compilation).hadWarningContaining(
                "Parameter \"unknownParam\" is not an AppSearch parameter; don't know how to "
                        + "supply it");
    }

    @Test
    public void testSuccess() throws Exception {
        Compilation compilation = compile(
                "@AppSearchDocument\n"
                        + "public class Gift {\n"
                        + "  Gift(boolean dog, String uri) {\n"
                        + "    this.uri = uri;\n"
                        + "    this.dog = dog;\n"
                        + "  }\n"
                        + "  @AppSearchDocument.Uri final String uri;\n"
                        + "  @AppSearchDocument.Property int price;\n"
                        + "  @AppSearchDocument.Property boolean cat = false;\n"
                        + "  public void setCat(boolean cat) {}\n"
                        + "  @AppSearchDocument.Property private final boolean dog;\n"
                        + "  public boolean getDog() { return dog; }\n"
                        + "}\n");
        CompilationSubject.assertThat(compilation).succeededWithoutWarnings();
        checkEqualsGolden();
    }

    private Compilation compile(String classBody) {
        return compile("Gift", classBody);
    }

    private Compilation compile(String classSimpleName, String classBody) {
        String src = "package com.example.appsearch;\n"
                + "import androidx.appsearch.annotation.AppSearchDocument;\n"
                + classBody;
        JavaFileObject jfo = JavaFileObjects.forSourceString(
                "com.example.appsearch." + classSimpleName,
                src);
        // Fully compiling this source code requires AppSearch to be on the classpath, but it only
        // builds on Android. Instead, this test configures the annotation processor to write to a
        // test-controlled path which is then diffed.
        String outputDirFlag = String.format(
                "-A%s=%s",
                DocumentProcessor.OUTPUT_DIR_OPTION,
                mGenFilesDir.getAbsolutePath());
        return Compiler.javac()
                .withProcessors(new DocumentProcessor())
                .withOptions(outputDirFlag)
                .compile(jfo);
    }

    private void checkEqualsGolden() throws IOException {
        // Get the expected file contents
        String goldenResPath = "goldens/" + mTestName.getMethodName() + ".JAVA";
        String expected = "";
        try (InputStream is = getClass().getResourceAsStream(goldenResPath)) {
            if (is == null) {
                LOG.warning("Failed to find resource \"" + goldenResPath + "\"; treating as empty");
            } else {
                InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
                expected = CharStreams.toString(reader);
            }
        }

        // Get the actual file contents
        File actualPackageDir = new File(mGenFilesDir, "com/example/appsearch");
        File actualPath = new File(actualPackageDir, CodeGenerator.GEN_CLASS_PREFIX + "Gift.java");
        Truth.assertWithMessage("Path " + actualPath + " is not a file")
                .that(actualPath.isFile()).isTrue();
        String actual = Files.asCharSource(actualPath, StandardCharsets.UTF_8).read();

        // Compare!
        if (expected.equals(actual)) {
            return;
        }

        // Sadness. If we're running in an environment where source is available, rewrite the golden
        // to match the actual content for ease of updating the goldens.
        try {
            // At runtime, our resources come from the build tree. However, our cwd is
            // frameworks/support, so find the source tree from that.
            File goldenSrcDir = new File("src/test/resources/androidx/appsearch/compiler");
            if (!goldenSrcDir.isDirectory()) {
                LOG.warning("Failed to update goldens: golden dir \""
                        + goldenSrcDir.getAbsolutePath() + "\" does not exist or is not a folder");
                return;
            }
            File goldenFile = new File(goldenSrcDir, goldenResPath);
            Files.asCharSink(goldenFile, StandardCharsets.UTF_8).write(actual);
            LOG.info("Successfully updated golden file \"" + goldenFile + "\"");
        } finally {
            // Now produce the real exception for the test runner.
            Truth.assertThat(actual).isEqualTo(expected);
        }
    }
}
