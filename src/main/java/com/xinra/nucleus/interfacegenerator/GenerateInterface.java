package com.xinra.nucleus.interfacegenerator;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Generates an interface that contains all public member methods of the annotated class.
 * Can only be used on top-level classes.
 * See <a href="https://github.com/xinra-nucleus/interface-generator/blob/master/README.md">
 * online documentation</a>.
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
@Inherited
@Documented
public @interface GenerateInterface {

  /**
   * Name of the generated interface. If this is not specified, the {@link #namingStrategy()}
   * is used to retrieve the interface name.
   * If the name is not fully qualified, the interface will be placed in the same package
   * as the annotated class.
   */
  String value() default "";
  
  /**
   * A closure that returns the name of the generated interface as a {@link String}. 
   * Parameter is a {@link TypeElement} of the annotated type.
   * 
   * @implSpec The default implementation will create the interface {@code IFoo} for the 
   * annotated class {@code Foo}.
   */
  String namingStrategy() default InterfaceNamingStrategy.PREFIX_I;
  
  /**
   * If true, for every property {@code fooBar} a constant 
   * <pre>public static String FooBar = "fooBar";</pre> is generated.
   */
  boolean propertyConstants() default false;
  
}
