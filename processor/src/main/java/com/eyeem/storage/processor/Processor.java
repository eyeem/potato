package com.eyeem.storage.processor;

import java.util.ArrayList;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;

@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedAnnotationTypes("com.eyeem.storage.annotation.Storage")
public class Processor extends AbstractProcessor implements CodeWriter.Log {
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        ArrayList<CodeWriter> writers = new ArrayList<>();
        Filer filer = processingEnv.getFiler();

        //region for every annotation (should be just the one)
        for (TypeElement annotation : annotations) {

            //region for every class annotated with the annotation
            for (Element element : roundEnv.getElementsAnnotatedWith(annotation)) {
                log("generating for " + element.getSimpleName());
                PackageElement packageElement = getPackage(element);
                TypeElement classElement = getClass(element);
                writers.add(new CodeWriter(this, packageElement, classElement, filer));
            }
            //endregion
        }
        //endregion


        for (CodeWriter writer : writers) {
            writer.run();
        }

        return true;
    }

    public static PackageElement getPackage(Element type) {
        while (type.getKind() != ElementKind.PACKAGE) {
            type = type.getEnclosingElement();
        }
        return (PackageElement) type;
    }

    public static TypeElement getClass(Element type) {
        while (type.getKind() != ElementKind.CLASS) {
            type = type.getEnclosingElement();
        }
        return (TypeElement) type;
    }

    public void log(String message) {
        log(message, null);
    }

    @Override
    public void log(String message, Throwable t) {
        if (t == null)
            processingEnv.getMessager().printMessage(Diagnostic.Kind.NOTE, message);
        else {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, message);
            for (StackTraceElement ste : t.getStackTrace())
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, ste.toString());
        }
    }
}
