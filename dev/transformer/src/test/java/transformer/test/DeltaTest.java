package transformer.test;

import java.io.PrintWriter;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import transformer.test.util.ClassData;
import transformer.test.util.ClassDelta;

public class DeltaTest {

	@Test
	public void testNullClassChange() {
		Class<?> testClass = Sample_InjectAPI_Jakarta.class;
		System.out.println("Test class [ " + testClass + " ]");

		ClassData testClassData = new ClassData(testClass);
		System.out.println("Test class data [ " + testClassData + " ]");

		ClassDelta testClassDelta = new ClassDelta(testClassData, testClassData);
		System.out.println("Test class delta [ " + testClassDelta + " ]");

		testClassDelta.log( new PrintWriter(System.out) );

		Assertions.assertTrue( testClassDelta.isNull(), "Delta of [ " + testClass + " ] is not null" );
	}

	@Test
	public void testClassChange() {
		Class<?> initialClass = Sample_InjectAPI_Javax.class;
		System.out.println("Initial class [ " + initialClass + " ]");

		ClassData initialClassData = new ClassData(initialClass);
		System.out.println("Initial class data [ " + initialClassData + " ] [ " + initialClassData.getHashText() + " ]");

		Class<?> finalClass = Sample_InjectAPI_Jakarta.class;
		System.out.println("Final class [ " + finalClass + " ]");

		ClassData finalClassData = new ClassData(finalClass);
		System.out.println("Final class data [ " + finalClassData + " ] [ " + finalClassData.getHashText() + " ]");

		ClassDelta classDelta = new ClassDelta(finalClassData, initialClassData);
		System.out.println("Class delta [ " + classDelta + " ] [ " + classDelta.getHashText() + " ]");
		classDelta.log( new PrintWriter(System.out, true) ); // autoflush

		Assertions.assertFalse(
			classDelta.isNull(),
			"Delta of [ " + finalClass + " ] with [ " + initialClass + " ] is null" );

		Assertions.assertFalse(
			classDelta.nullClassNameChange(),
			"Class name delta of [ " + finalClass + " ] with [ " + initialClass + " ] is null" );
		Assertions.assertTrue(
			classDelta.nullSuperclassNameChange(),
			"Superclass name delta of [ " + finalClass + " ] with [ " + initialClass + " ] is not null" );
		Assertions.assertTrue(
			classDelta.nullInterfaceNameChanges(),
			"Interface name delta of [ " + finalClass + " ] with [ " + initialClass + " ] is not null" );

		Assertions.assertTrue(
			classDelta.nullFieldChanges(),
			"Field delta of [ " + finalClass + " ] with [ " + initialClass + " ] is not null" );
		Assertions.assertFalse(
			classDelta.nullFieldAnnotationChanges(),
			"Field annotation delta of [ " + finalClass + " ] with [ " + initialClass + " ] is null" );

		Assertions.assertFalse(
			classDelta.nullMethodChanges(),
			"Method delta of [ " + finalClass + " ] with [ " + initialClass + " ] is null" );
		Assertions.assertTrue(
			classDelta.nullMethodAnnotationChanges(),
			"Method annotation delta of [ " + finalClass + " ] with [ " + initialClass + " ] is not null" );

		Assertions.assertFalse(
			classDelta.nullInitChanges(),
			"Init delta of [ " + finalClass + " ] with [ " + initialClass + " ] is null" );
		Assertions.assertFalse(
			classDelta.nullInitAnnotationChanges(),
			"Init annotation delta of [ " + finalClass + " ] with [ " + initialClass + " ] is null" );

		Assertions.assertFalse(
			classDelta.nullStaticMethodChanges(),
			"Static method delta of [ " + finalClass + " ] with [ " + initialClass + " ] is null" );
		Assertions.assertTrue(
			classDelta.nullStaticMethodAnnotationChanges(),
			"Static method annotation delta of [ " + finalClass + " ] with [ " + initialClass + " ] is not null" );
	}

}