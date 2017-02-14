package com.xinra.nucleus.interfacegenerator;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.xinra.nucleus.apt.NucleusProcessor;
import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeVariable;

/**
 * Annotation processor that generates interfaces for classes annotated
 * with {@link GenerateInterface}.
 */
public class InterfaceGenerator extends NucleusProcessor {
  
  private static final List<String> IGNORED_METHODS = Arrays.asList(
        "<init>", //constructor
        "getMetaClass",
        "setMetaClass",
        "invokeMethod",
        "getProperty",
        "setProperty"
      );
  
  private static final List<String> IGNORED_INTERFACES = Arrays.asList(
        "GroovyObject"
      );
  
  private class NamingStrategyException extends Exception {
    private static final long serialVersionUID = 1L;
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    for (Element annotatedElement : roundEnv.getElementsAnnotatedWith(GenerateInterface.class)) {
      
      if (annotatedElement.getKind() != ElementKind.CLASS) {
        error(annotatedElement, "Only classes can be annotated with @%s",
            GenerateInterface.class.getSimpleName());
        return true;
      }
      
      TypeElement annotatedType = (TypeElement) annotatedElement;
      
      if (annotatedType.getEnclosingElement().getKind() != ElementKind.PACKAGE) {
        error(annotatedElement, "Only top-level classes can be annotated with @%s",
            GenerateInterface.class.getSimpleName());
        return true;
      }
              
      ClassName interfaceName;
      try {
        interfaceName = getClassName(annotatedElement);
      } catch (NamingStrategyException ex) {
        return true;
      }
      
      boolean generatePropertyConstants 
          = annotatedType.getAnnotation(GenerateInterface.class).propertyConstants();
      
      try {
        TypeSpec.Builder typeBuilder = TypeSpec.interfaceBuilder(interfaceName.simpleName());
        typeBuilder.addModifiers(Modifier.PUBLIC);
      
        //if the annotated class extends a class that is also annotated,
        //the generated interface of that class becomes a super interface of this one
        Element parent = ((DeclaredType) annotatedType.getSuperclass()).asElement();
        if (parent.getAnnotation(GenerateInterface.class) != null) {
          try {
            ClassName parentName = getClassName(parent);
            typeBuilder.addSuperinterface(parentName);
          } catch (NamingStrategyException ex) {
            return true;
          }
        }
      
        //all implemented interfaces become super interfaces
        annotatedType.getInterfaces().forEach(i -> {
          String simpleName = ((DeclaredType) i).asElement().getSimpleName().toString();
          //skip the generated interface itself (loop inheritance)
          //this will also skip interfaces with the same name but a different package
          if (simpleName.equals(interfaceName.simpleName())) {
            return;
          }
          //skip ignored interfaces
          if (IGNORED_INTERFACES.contains(simpleName)) {
            return;
          }
          typeBuilder.addSuperinterface(TypeName.get(i));
        });
      
        //copy type parameters
        annotatedType.getTypeParameters().stream()
            .map(TypeVariableName::get)
            .forEach(typeBuilder::addTypeVariable);
      
        //copy methods (see MethodSpec#overriding())
        annotatedType.getEnclosedElements().forEach(e -> {
          if (e.getKind() != ElementKind.METHOD) {
            return;
          }
          ExecutableElement method = (ExecutableElement) e;
          if (IGNORED_METHODS.contains(method.getSimpleName().toString()) //skip ignored methods
              || !method.getModifiers().contains(Modifier.PUBLIC) //only copy public methods
              || method.getModifiers().contains(Modifier.STATIC) //only copy member methods
              || method.getAnnotation(NotExposed.class) != null) {  //skip explicitly ignored m.
            return; 
          }
          
          String methodName = method.getSimpleName().toString();
          MethodSpec.Builder methodBuilder = MethodSpec.methodBuilder(methodName);
          methodBuilder.addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);
          
          method.getTypeParameters().stream()
              .map(p -> (TypeVariable) p.asType())
              .map(TypeVariableName::get)
              .forEach(methodBuilder::addTypeVariable);
          
          methodBuilder.returns(TypeName.get(method.getReturnType()));
          
          method.getParameters().forEach(p -> {
            ParameterSpec.Builder parameterBuilder = ParameterSpec.builder(
                TypeName.get(p.asType()),
                p.getSimpleName().toString(),
                p.getModifiers().toArray(new Modifier[p.getModifiers().size()]));
            p.getAnnotationMirrors().stream()
                .map(AnnotationSpec::get)
                .forEach(parameterBuilder::addAnnotation);
            methodBuilder.addParameter(parameterBuilder.build());
          });
          
          methodBuilder.varargs(method.isVarArgs());

          method.getThrownTypes().stream()
              .map(TypeName::get)
              .forEach(methodBuilder::addException);
          
          typeBuilder.addMethod(methodBuilder.build());
          
          //if requested, for every property, a String constant containing the property id is added
          if (generatePropertyConstants && methodName.startsWith("set")) {
            String constantName = methodName.substring(3);
            //make first character lower case
            String propertyId = constantName.substring(0, 1).toLowerCase() 
                + constantName.substring(1);
            FieldSpec constant = FieldSpec.builder(ClassName.get(String.class), constantName,
                Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                .initializer("$S", propertyId)
                .build();
            typeBuilder.addField(constant);
          }
        });
        JavaFile jf = JavaFile.builder(interfaceName.packageName(), typeBuilder.build()).build();
        jf.writeTo(processingEnv.getFiler());
      } catch (IOException ioe) {
        error(null, ioe.getMessage());
      }
    }
    
    return false;
  }
  
  /**
   * Returns the name of the interface that should be generated for a type
   * annotated with {@link GenerateInterface} (see doc of {@link GenerateInterface}).
   * @throws NamingStrategyException if the namingStrategy threw an exception or
   *     returned null or empty string.
   */
  @SuppressWarnings("unchecked")
  private ClassName getClassName(Element element) throws NamingStrategyException {
    GenerateInterface annotation = element.getAnnotation(GenerateInterface.class);
    String value = annotation.value();
    if (value.isEmpty()) {
      try {
        value = ((Closure<String>) new GroovyShell().evaluate(annotation.namingStrategy()))
            .call(element);
      } catch (Exception ex) {
        error(element, "namingStrategy is not a valid Groovy closure: %s", ex.getMessage());
        throw new NamingStrategyException();
      }
        
      if (value == null || value.isEmpty()) {
        error(element, "The name of the generated interface cannot be empty");
        throw new NamingStrategyException();
      }
    }
    
    int lastSeperatorIndex = value.lastIndexOf('.');
    if (lastSeperatorIndex == -1) {
      return ClassName.get(((PackageElement) element.getEnclosingElement())
          .getQualifiedName().toString(), value);
    } else {
      return ClassName.get(value.substring(0, lastSeperatorIndex),
          value.substring(lastSeperatorIndex + 1));
    }
  }
  
  @Override
  public Set<String> getSupportedAnnotationTypes() {
    Set<String> types = new HashSet<>();
    types.add(GenerateInterface.class.getCanonicalName());
    return Collections.unmodifiableSet(types);
  }

}
