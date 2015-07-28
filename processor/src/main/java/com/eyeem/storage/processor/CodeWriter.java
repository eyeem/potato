package com.eyeem.storage.processor;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;

import javax.annotation.processing.Filer;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

/**
 * Created by budius on 28.07.15.
 */
public class CodeWriter implements Runnable {

    private static final String ID = "@com.eyeem.storage.annotation.Id";

    private final Filer filer;

    private String id;
    private final ClassName generatedClass;
    private final ClassName dataClass;
    private final ClassName context;
    private final String packageName;
    private final String generatedClassName;


    private final Log log;

    public CodeWriter(Log log, PackageElement packageElement, TypeElement classElement, Filer filer) {
        this.filer = filer;
        this.log = log;
        packageName = packageElement.getQualifiedName().toString();
        String dataClassName = classElement.getSimpleName().toString();
        generatedClassName = "Storage" + dataClassName;
        this.id = "id";

        generatedClass = ClassName.get(packageName, generatedClassName);
        dataClass = ClassName.get(packageName, dataClassName);
        context = ClassName.get("android.content", "Context");

        for (Element enclosedElement : classElement.getEnclosedElements()) {
            log("checking element: " + enclosedElement.toString() + "; " + enclosedElement.asType().toString());
            for (AnnotationMirror am : enclosedElement.getAnnotationMirrors()) {
                if (ID.equals(am.toString())) {
                    // found the ID
                    id = enclosedElement.toString();
                    if (!enclosedElement.asType().toString().contains("java.lang.String")) {
                        id = id + ".toString()";
                    }
                }
                log("mirror: " + am.toString() + "; name: " + am.getAnnotationType().asElement().getSimpleName());
            }
        }
    }

    @Override
    public void run() {
        try {
            log("executing for " + generatedClassName);
            process();
            log("complete");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            err("ClassNotFoundException", e);
        } catch (IOException e) {
            e.printStackTrace();
            err("IOException", e);
        } catch (Throwable t) {
            err("Throwable", t);
        }
    }

    private void process() throws ClassNotFoundException, IOException {

        // Build fields
        ArrayList<FieldSpec> fields = new ArrayList<>();
        FieldSpec field;
        //FieldSpec.builder(String.class, "instance", Modifier.PRIVATE, Modifier.STATIC);
        field = FieldSpec
                .builder(generatedClass, "instance")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC)
                .build();
        fields.add(field);


        // Build methods
        ArrayList<MethodSpec> methods = new ArrayList<>();
        MethodSpec method;

        method = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PRIVATE)
                .addParameter(context, "context")
                .addStatement("super(context)")
                .build();
        methods.add(method);

        method = MethodSpec.methodBuilder("id")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(dataClass, "object")
                .returns(String.class)
                .addStatement("return object." + id)
                .build();
        methods.add(method);

        method = MethodSpec.methodBuilder("classname")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(Class.class)
                .addStatement("return $T.class", dataClass)
                .build();
        methods.add(method);

        method = MethodSpec.methodBuilder("get")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addParameter(context, "context")
                .returns(generatedClass)
                .addStatement("if(instance == null) initialise(context)")
                .addStatement("return instance")
                .build();
        methods.add(method);

        method = MethodSpec.methodBuilder("initialise")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.SYNCHRONIZED)
                .addParameter(context, "context")
                .beginControlFlow("if(instance == null)")
                .addStatement("instance = new $T(context)", generatedClass)
                .addStatement("instance.init()")
                .endControlFlow()
                .build();
        methods.add(method);

        // build class
        TypeSpec.Builder builder = TypeSpec.classBuilder(generatedClassName)
                .addModifiers(Modifier.PUBLIC)
                .superclass(ParameterizedTypeName.get(ClassName.get("com.eyeem.storage", "Storage"), dataClass));
        for (FieldSpec fieldSpec : fields) builder.addField(fieldSpec);
        for (MethodSpec methodSpec : methods) builder.addMethod(methodSpec);
        TypeSpec typeSpec = builder.build();

        JavaFile javaFile = JavaFile.builder(packageName, typeSpec).build();

        Writer writer = filer.createSourceFile(packageName + "." + generatedClassName).openWriter();
        javaFile.writeTo(writer);
        writer.close();

    }

    private void log(String message) {
        log.log(message, null);
    }

    private void err(String message, Throwable t) {
        log.log(message, t);
    }

    public interface Log {
        public void log(String message, Throwable t);
    }
}
