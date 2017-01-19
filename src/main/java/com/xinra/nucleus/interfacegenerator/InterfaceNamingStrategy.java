package com.xinra.nucleus.interfacegenerator;

/**
 * Predefines common values for {@link GenerateInterface#namingStrategy()}.
 */
public final class InterfaceNamingStrategy {
  
  /**
   * Will create the interface {@code Foo} for the annotated class {@code FooImpl}.
   */
  public static final String EXCEPT_LAST_FOUR_CHARS 
      = "{it -> it.getSimpleName().toString().substring(0, it.getSimpleName().length()-4)}";
  
  /**
   * Will create the interface {@code Foo} for the annotated class {@code _Foo}.
   */
  public static final String EXCEPT_FIRST_CHAR 
      = "{it -> it.getSimpleName().toString().substring(1)}";
  
  /**
   * Will create the interface {@code IFoo} for the annotated class {@code Foo}.
   */
  public static final String PREFIX_I = "{it -> 'I' + it.getSimpleName()}";
  
  private InterfaceNamingStrategy() {}
  
}
