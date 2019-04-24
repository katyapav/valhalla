/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package compiler.valhalla.valuetypes;

import jdk.test.lib.Asserts;

/*
 * @test
 * @summary Test calls from {C1} to {C2, Interpreter}, and vice versa.
 * @library /testlibrary /test/lib /compiler/whitebox /
 * @requires os.simpleArch == "x64"
 * @compile TestCallingConventionC1.java
 * @run driver ClassFileInstaller sun.hotspot.WhiteBox jdk.test.lib.Platform
 * @run main/othervm/timeout=120 -Xbootclasspath/a:. -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions
 *                               -XX:+UnlockExperimentalVMOptions -XX:+WhiteBoxAPI -XX:+EnableValhalla
 *                               compiler.valhalla.valuetypes.ValueTypeTest
 *                               compiler.valhalla.valuetypes.TestCallingConventionC1
 */
public class TestCallingConventionC1 extends ValueTypeTest {
    public static final int C1 = COMP_LEVEL_SIMPLE;
    public static final int C2 = COMP_LEVEL_FULL_OPTIMIZATION;

    @Override
    public int getNumScenarios() {
        return 2;
    }

    @Override
    public String[] getVMParameters(int scenario) {
        switch (scenario) {

        // Default: both C1 and C2 are enabled, tierd compilation enabled
        case 0: return new String[] {"-XX:+EnableValhallaC1", "-XX:CICompilerCount=2"
                                     , "-XX:-CheckCompressedOops", "-XX:CompileCommand=print,*::test47*"
                                   //, "-XX:CompileCommand=print,*::func_c1"
                                     };
        // Only C1. Tierd compilation disabled.
        case 1: return new String[] {"-XX:+EnableValhallaC1", "-XX:TieredStopAtLevel=1"
                                     , "-XX:-CheckCompressedOops", "-XX:CompileCommand=print,*::test32*"
                                     };
        }
        return null;
    }

    public static void main(String[] args) throws Throwable {
        TestCallingConventionC1 test = new TestCallingConventionC1();
        test.run(args,
                 Point.class,
                 Functor.class,
                 Functor1.class,
                 Functor2.class,
                 Functor3.class,
                 Functor4.class,
                 MyImplPojo1.class,
                 MyImplPojo2.class,
                 MyImplVal.class,
                 FixedPoints.class,
                 FloatPoint.class);
    }

    static inline class Point {
        final int x;
        final int y;
        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @DontCompile
        @DontInline
        public int func() {
            return x + y;
        }

        @ForceCompile(compLevel = C1)
        @DontInline
        public int func_c1(Point p) {
            return x + y + p.x + p.y;
        }
    }

    static interface FunctorInterface {
        public int apply_interp(Point p);
    }

    static class Functor implements FunctorInterface {
        @DontCompile
        @DontInline
        public int apply_interp(Point p) {
            return p.func() + 0;
        }
    }
    static class Functor1 extends Functor {
        @DontCompile
        @DontInline
        public int apply_interp(Point p) {
            return p.func() + 10000;
        }
    }
    static class Functor2 extends Functor {
        @DontCompile
        @DontInline
        public int apply_interp(Point p) {
            return p.func() + 20000;
        }
    }
    static class Functor3 extends Functor {
        @DontCompile
        @DontInline
        public int apply_interp(Point p) {
            return p.func() + 30000;
        }
    }
    static class Functor4 extends Functor {
        @DontCompile
        @DontInline
        public int apply_interp(Point p) {
            return p.func() + 40000;
        }
    }

    static Functor functors[] = {
        new Functor(),
        new Functor1(),
        new Functor2(),
        new Functor3(),
        new Functor4()
    };
    static int functorCounter = 0;
    static Functor getFunctor() {
        int n = (++ functorCounter) % functors.length;
        return functors[n];
    }

    static Point pointField  = new Point(123, 456);
    static Point pointField1 = new Point(1123, 1456);
    static Point pointField2 = new Point(2123, 2456);

    static interface Intf {
        public int func1(int a, int b);
        public int func2(int a, int b, Point p);
    }

    static class MyImplPojo1 implements Intf {
        int field = 1000;
        @DontInline @DontCompile
        public int func1(int a, int b)             { return field + a + b + 1; }
        @DontInline @DontCompile
        public int func2(int a, int b, Point p)     { return field + a + b + p.x + p.y + 1; }
    }

    static class MyImplPojo2 implements Intf {
        int field = 2000;

        @DontInline @ForceCompile(compLevel = C1)
        public int func1(int a, int b)             { return field + a + b + 20; }
        @DontInline @ForceCompile(compLevel = C1)
        public int func2(int a, int b, Point p)    { return field + a + b + p.x + p.y + 20; }
    }

    static inline class MyImplVal implements Intf {
        final int field;
        MyImplVal(int f) {
            field = f;
        }
        MyImplVal() {
            field = 3000;
        }

        @DontInline @ForceCompile(compLevel = C1)
        public int func1(int a, int b)             { return field + a + b + 300; }

        @DontInline @ForceCompile(compLevel = C1)
        public int func2(int a, int b, Point p)    { return field + a + b + p.x + p.y + 300; }
    }

    static Intf intfs[] = {
        new MyImplPojo1(),
        new MyImplPojo2(),
        new MyImplVal()
    };
    static int intfCounter = 0;
    static Intf getIntf() {
        int n = (++ intfCounter) % intfs.length;
        return intfs[n];
    }

    static inline class FixedPoints {
        final boolean Z0 = false;
        final boolean Z1 = true;
        final byte    B  = (byte)2;
        final char    C  = (char)34;
        final short   S  = (short)456;
        final int     I  = 5678;
        final long    J  = 0x1234567800abcdefL;
    }
    static FixedPoints fixedPointsField = new FixedPoints();

    static inline class FloatPoint {
        final float x;
        final float y;
        public FloatPoint(float x, float y) {
            this.x = x;
            this.y = y;
        }
    }
    static inline class DoublePoint {
        final double x;
        final double y;
        public DoublePoint(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }
    static FloatPoint floatPointField = new FloatPoint(123.456f, 789.012f);
    static DoublePoint doublePointField = new DoublePoint(123.456, 789.012);

    static inline class EightFloats {
        float f1, f2, f3, f4, f5, f6, f7, f8;
        public EightFloats() {
            f1 = 1.1f;
            f2 = 2.2f;
            f3 = 3.3f;
            f4 = 4.4f;
            f5 = 5.5f;
            f6 = 6.6f;
            f7 = 7.7f;
            f8 = 8.8f;
        }
    }
    static EightFloats eightFloatsField = new EightFloats();

    //**********************************************************************
    // PART 1 - C1 calls interpreted code
    //**********************************************************************


    //** C1 passes value to interpreter (static)
    @Test(compLevel = C1)
    public int test1() {
        return test1_helper(pointField);
    }

    @DontInline
    @DontCompile
    private static int test1_helper(Point p) {
        return p.func();
    }

    @DontCompile
    public void test1_verifier(boolean warmup) {
        int count = warmup ? 1 : 10;
        for (int i=0; i<count; i++) { // need a loop to test inline cache
            int result = test1() + i;
            Asserts.assertEQ(result, pointField.func() + i);
        }
    }


    //** C1 passes value to interpreter (monomorphic)
    @Test(compLevel = C1)
    public int test2() {
        return test2_helper(pointField);
    }

    @DontInline
    @DontCompile
    private int test2_helper(Point p) {
        return p.func();
    }

    @DontCompile
    public void test2_verifier(boolean warmup) {
        int count = warmup ? 1 : 10;
        for (int i=0; i<count; i++) { // need a loop to test inline cache
            int result = test2() + i;
            Asserts.assertEQ(result, pointField.func() + i);
        }
    }

    // C1 passes value to interpreter (megamorphic: vtable)
    @Test(compLevel = C1)
    public int test3(Functor functor) {
        return functor.apply_interp(pointField);
    }

    @DontCompile
    public void test3_verifier(boolean warmup) {
        int count = warmup ? 1 : 100;
        for (int i=0; i<count; i++) {  // need a loop to test inline cache and vtable indexing
            Functor functor = warmup ? functors[0] : getFunctor();
            int result = test3(functor) + i;
            Asserts.assertEQ(result, functor.apply_interp(pointField) + i);
        }
    }

    // Same as test3, but compiled with C2. Test the hastable of VtableStubs
    @Test(compLevel = C2)
    public int test3b(Functor functor) {
        return functor.apply_interp(pointField);
    }

    @DontCompile
    public void test3b_verifier(boolean warmup) {
        int count = warmup ? 1 : 100;
        for (int i=0; i<count; i++) {  // need a loop to test inline cache and vtable indexing
            Functor functor = warmup ? functors[0] : getFunctor();
            int result = test3b(functor) + i;
            Asserts.assertEQ(result, functor.apply_interp(pointField) + i);
        }
    }

    // C1 passes value to interpreter (megamorphic: itable)
    @Test(compLevel = C1)
    public int test4(FunctorInterface fi) {
        return fi.apply_interp(pointField);
    }

    @DontCompile
    public void test4_verifier(boolean warmup) {
        int count = warmup ? 1 : 100;
        for (int i=0; i<count; i++) {  // need a loop to test inline cache and itable indexing
            Functor functor = warmup ? functors[0] : getFunctor();
            int result = test4(functor) + i;
            Asserts.assertEQ(result, functor.apply_interp(pointField) + i);
        }
    }

    //**********************************************************************
    // PART 2 - interpreter calls C1
    //**********************************************************************

    // Interpreter passes value to C1 (static)
    @Test(compLevel = C1)
    static public int test20(Point p1, long l, Point p2) {
        return p1.x + p2.y;
    }

    @DontCompile
    public void test20_verifier(boolean warmup) {
        int result = test20(pointField1, 0, pointField2);
        int n = pointField1.x + pointField2.y;
        Asserts.assertEQ(result, n);
    }

    // Interpreter passes value to C1 (instance method in inline class)
    @Test
    public int test21(Point p) {
        return test21_helper(p);
    }

    @DontCompile
    @DontInline
    int test21_helper(Point p) {
        return p.func_c1(p);
    }

    @DontCompile
    public void test21_verifier(boolean warmup) {
        int result = test21(pointField);
        int n = 2 * (pointField.x + pointField.y);
        Asserts.assertEQ(result, n);
    }


    //**********************************************************************
    // PART 3 - C2 calls C1
    //**********************************************************************

    // C2->C1 invokestatic, single value arg
    @Test(compLevel = C2)
    public int test30() {
        return test30_helper(pointField);
    }

    @DontInline
    @ForceCompile(compLevel = C1)
    private static int test30_helper(Point p) {
        return p.x + p.y;
    }

    @DontCompile
    public void test30_verifier(boolean warmup) {
        int count = warmup ? 1 : 2;
        for (int i=0; i<count; i++) { // need a loop to test inline cache
            int result = test30();
            int n = pointField.x + pointField.y;
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, two single value args
    @Test(compLevel = C2)
      public int test31() {
      return test31_helper(pointField1, pointField2);
    }

    @DontInline
    @ForceCompile(compLevel = C1)
      private static int test31_helper(Point p1, Point p2) {
        return p1.x + p2.y;
    }

    @DontCompile
    public void test31_verifier(boolean warmup) {
        int count = warmup ? 1 : 2;
        for (int i=0; i<count; i++) { // need a loop to test inline cache
            int result = test31();
            int n = pointField1.x + pointField2.y;
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, two single value args and interleaving ints (all passed in registers on x64)
    @Test(compLevel = C2)
    public int test32() {
      return test32_helper(0, pointField1, 1, pointField2);
    }

    @DontInline
    @ForceCompile(compLevel = C1)
    private static int test32_helper(int x, Point p1, int y, Point p2) {
        return p1.x + p2.y + x + y;
    }

    @DontCompile
    public void test32_verifier(boolean warmup) {
        int count = warmup ? 1 : 2;
        for (int i=0; i<count; i++) { // need a loop to test inline cache
            int result = test32();
            int n = pointField1.x + pointField2.y + 0 + 1;
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokeinterface -- no verified_ro_entry (no value args except for receiver)
    @Test(compLevel = C2)
    public int test33(Intf intf, int a, int b) {
        return intf.func1(a, b);
    }

    @DontCompile
    public void test33_verifier(boolean warmup) {
        int count = warmup ? 1 : 20;
        for (int i=0; i<count; i++) {
            Intf intf = warmup ? intfs[0] : getIntf();
            int result = test33(intf, 123, 456) + i;
            Asserts.assertEQ(result, intf.func1(123, 456) + i);
        }
    }

    // C2->C1 invokeinterface -- use verified_ro_entry (has value args other than receiver)
    @Test(compLevel = C2)
    public int test34(Intf intf, int a, int b) {
        return intf.func2(a, b, pointField);
    }

    @DontCompile
    public void test34_verifier(boolean warmup) {
        int count = warmup ? 1 : 20;
        for (int i=0; i<count; i++) {
            Intf intf = warmup ? intfs[0] : getIntf();
            int result = test34(intf, 123, 456) + i;
            Asserts.assertEQ(result, intf.func2(123, 456, pointField) + i);
        }
    }

    // C2->C1 invokestatic, Point.y is on stack (x64)
    @Test(compLevel = C2)
    public int test35() {
        return test35_helper(1, 2, 3, 4, 5, pointField);
    }

    @DontInline
    @ForceCompile(compLevel = C1)
    private static int test35_helper(int a1, int a2, int a3, int a4, int a5, Point p) {
        return a1 + a2 + a3 + a4 + a5 + p.x + p.y;
    }

    @DontCompile
    public void test35_verifier(boolean warmup) {
        int count = warmup ? 1 : 2;
        for (int i=0; i<count; i++) { // need a loop to test inline cache
            int result = test35();
            int n = 1 + 2 + 3  + 4 + 5 + pointField.x + pointField.y;
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, shuffling arguments that are passed on stack
    @Test(compLevel = C2)
    public int test36() {
        return test36_helper(pointField, 1, 2, 3, 4, 5, 6, 7, 8);
    }

    @DontInline
    @ForceCompile(compLevel = C1)
    private static int test36_helper(Point p, int a1, int a2, int a3, int a4, int a5, int a6, int a7, int a8) {
        return a6 + a8;
    }

    @DontCompile
    public void test36_verifier(boolean warmup) {
        int count = warmup ? 1 : 2;
        for (int i=0; i<count; i++) { // need a loop to test inline cache
            int result = test36();
            int n = 6 + 8;
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, shuffling long arguments
    @Test(compLevel = C2)
    public int test37() {
        return test37_helper(pointField, 1, 2, 3, 4, 5, 6, 7, 8);
    }

    @DontInline
    @ForceCompile(compLevel = C1)
    private static int test37_helper(Point p, long a1, long a2, long a3, long a4, long a5, long a6, long a7, long a8) {
        return (int)(a6 + a8);
    }

    @DontCompile
    public void test37_verifier(boolean warmup) {
        int count = warmup ? 1 : 2;
        for (int i=0; i<count; i++) { // need a loop to test inline cache
            int result = test37();
            int n = 6 + 8;
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, shuffling boolean, byte, char, short, int, long arguments
    @Test(compLevel = C2)
    public int test38() {
        return test38_helper(pointField, true, (byte)1, (char)2, (short)3, 4, 5, (byte)6, (short)7, 8);
    }

    @DontInline
    @ForceCompile(compLevel = C1)
    private static int test38_helper(Point p, boolean a0, byte a1, char a2, short a3, int a4, long a5, byte a6, short a7, int a8) {
        if (a0) {
            return (int)(a1 + a2 + a3 + a4 + a5 + a6 + a7 + a8);
        } else {
            return -1;
        }
    }

    @DontCompile
    public void test38_verifier(boolean warmup) {
        int count = warmup ? 1 : 2;
        for (int i=0; i<count; i++) { // need a loop to test inline cache
            int result = test38();
            int n = 1 + 2 + 3 + 4 + 5 + 6 + 7 + 8;
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, packing a value object with all types of fixed point primitive fields.
    @Test(compLevel = C2)
    public long test39() {
        return test39_helper(1, fixedPointsField, 2, fixedPointsField);
    }

    @DontInline
    @ForceCompile(compLevel = C1)
    private static long test39_helper(int a1, FixedPoints f1, int a2, FixedPoints f2) {
        if (f1.Z0 == false && f1.Z1 == true && f2.Z0 == false && f2.Z1 == true) {
            return f1.B + f2.C + f1.S + f2.I + f1.J;
        } else {
            return -1;
        }
    }

    @DontCompile
    public void test39_verifier(boolean warmup) {
        int count = warmup ? 1 : 2;
        for (int i=0; i<count; i++) { // need a loop to test inline cache
            long result = test39();
            long n = test39_helper(1, fixedPointsField, 2, fixedPointsField);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, shuffling floating point args
    @Test(compLevel = C2)
    public double test40() {
        return test40_helper(1.1f, 1.2, floatPointField, doublePointField, 1.3f, 1.4, 1.5f, 1.7, 1.7, 1.8, 1.9, 1.10, 1.11, 1.12);
    }

    @DontInline
    @ForceCompile(compLevel = C1)
    private static double test40_helper(float a1, double a2, FloatPoint fp, DoublePoint dp, float a3, double a4, float a5, double a6, double a7, double a8, double a9, double a10, double a11, double a12) {
        return a1 + a2 + a3 + a4 + a5 + a6 + a7 + a8 + a9 + a10 + a11 + a12 + fp.x + fp.y - dp.x - dp.y;
    }

    @DontCompile
    public void test40_verifier(boolean warmup) {
        int count = warmup ? 1 : 2;
        for (int i=0; i<count; i++) { // need a loop to test inline cache
            double result = test40();
            double n = test40_helper(1.1f, 1.2, floatPointField, doublePointField, 1.3f, 1.4, 1.5f, 1.7, 1.7, 1.8, 1.9, 1.10, 1.11, 1.12);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, mixing floats and ints
    @Test(compLevel = C2)
    public double test41() {
        return test41_helper(1, 1.2, pointField, floatPointField, doublePointField, 1.3f, 4, 1.5f, 1.7, 1.7, 1.8, 9, 1.10, 1.11, 1.12);
    }

    @DontInline
    @ForceCompile(compLevel = C1)
    private static double test41_helper(int a1, double a2, Point p, FloatPoint fp, DoublePoint dp, float a3, int a4, float a5, double a6, double a7, double a8, long a9, double a10, double a11, double a12) {
      return a1 + a2  + fp.x + fp.y - dp.x - dp.y + a3 + a4 + a5 + a6 + a7 + a8 + a9 + a10 + a11 + a12;
    }

    @DontCompile
    public void test41_verifier(boolean warmup) {
        int count = warmup ? 1 : 2;
        for (int i=0; i<count; i++) { // need a loop to test inline cache
            double result = test41();
            double n = test41_helper(1, 1.2, pointField, floatPointField, doublePointField, 1.3f, 4, 1.5f, 1.7, 1.7, 1.8, 9, 1.10, 1.11, 1.12);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, circular dependency (between rdi and first stack slot on x64)
    @Test(compLevel = C2)
    public float test42() {
        return test42_helper(eightFloatsField, pointField, 3, 4, 5, floatPointField, 7);
    }

    @DontInline
    @ForceCompile(compLevel = C1)
    private static float test42_helper(EightFloats ep1, // (xmm0 ... xmm7) -> rsi
                                       Point p2,        // (rsi, rdx) -> rdx
                                       int i3,          // rcx -> rcx
                                       int i4,          // r8 -> r8
                                       int i5,          // r9 -> r9
                                       FloatPoint fp6,  // (stk[0], stk[1]) -> rdi   ** circ depend
                                       int i7)          // rdi -> stk[0]             ** circ depend
    {
        return ep1.f1 + ep1.f2 + ep1.f3 + ep1.f4 + ep1.f5 + ep1.f6 + ep1.f7 + ep1.f8 +
            p2.x + p2.y + i3 + i4 + i5 + fp6.x + fp6.y + i7;
    }

    @DontCompile
    public void test42_verifier(boolean warmup) {
        int count = warmup ? 1 : 2;
        for (int i=0; i<count; i++) { // need a loop to test inline cache
            float result = test42();
            float n = test42_helper(eightFloatsField, pointField, 3, 4, 5, floatPointField, 7);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, packing causes stack growth (1 extra stack word)
    @Test(compLevel = C2)
    public float test43() {
        return test43_helper(floatPointField, 1, 2, 3, 4, 5, 6);
    }

    @DontInline
    @ForceCompile(compLevel = C1)
    private static float test43_helper(FloatPoint fp, int a1, int a2, int a3, int a4, int a5, int a6) {
        // On x64:
        //    Scalarized entry -- all parameters are passed in registers
        //    Non-scalarized entry -- a6 is passed on stack[0]
        return fp.x + fp.y + a1 + a2 + a3 + a4 + a5 + a6;
    }

    @DontCompile
    public void test43_verifier(boolean warmup) {
        int count = warmup ? 1 : 2;
        for (int i=0; i<count; i++) { // need a loop to test inline cache
            float result = test43();
            float n = test43_helper(floatPointField, 1, 2, 3, 4, 5, 6);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, packing causes stack growth (2 extra stack words)
    @Test(compLevel = C2)
    public float test44() {
      return test44_helper(floatPointField, floatPointField, 1, 2, 3, 4, 5, 6);
    }

    @DontInline
    @ForceCompile(compLevel = C1)
      private static float test44_helper(FloatPoint fp1, FloatPoint fp2, int a1, int a2, int a3, int a4, int a5, int a6) {
        // On x64:
        //    Scalarized entry -- all parameters are passed in registers
        //    Non-scalarized entry -- a5 is passed on stack[0]
        //    Non-scalarized entry -- a6 is passed on stack[1]
        return fp1.x + fp1.y +
               fp2.x + fp2.y +
               a1 + a2 + a3 + a4 + a5 + a6;
    }

    @DontCompile
    public void test44_verifier(boolean warmup) {
        int count = warmup ? 1 : 2;
        for (int i=0; i<count; i++) { // need a loop to test inline cache
            float result = test44();
            float n = test44_helper(floatPointField, floatPointField, 1, 2, 3, 4, 5, 6);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, packing causes stack growth (5 extra stack words)
    @Test(compLevel = C2)
    public float test45() {
      return test45_helper(floatPointField, floatPointField, floatPointField, floatPointField, floatPointField, 1, 2, 3, 4, 5, 6, 7);
    }

    @DontInline
    @ForceCompile(compLevel = C1)
    private static float test45_helper(FloatPoint fp1, FloatPoint fp2, FloatPoint fp3, FloatPoint fp4, FloatPoint fp5, int a1, int a2, int a3, int a4, int a5, int a6, int a7) {
        return fp1.x + fp1.y +
               fp2.x + fp2.y +
               fp3.x + fp3.y +
               fp4.x + fp4.y +
               fp5.x + fp5.y +
               a1 + a2 + a3 + a4 + a5 + a6 + a7;
    }

    @DontCompile
    public void test45_verifier(boolean warmup) {
        int count = warmup ? 1 : 2;
        for (int i=0; i<count; i++) { // need a loop to test inline cache
            float result = test45();
            float n = test45_helper(floatPointField, floatPointField, floatPointField, floatPointField, floatPointField, 1, 2, 3, 4, 5, 6, 7);
            Asserts.assertEQ(result, n);
        }
    }

    // C2->C1 invokestatic, packing causes stack growth (1 extra stack word -- mixing Point and FloatPoint)
    @Test(compLevel = C2)
    public float test46() {
      return test46_helper(floatPointField, floatPointField, pointField, floatPointField, floatPointField, pointField, floatPointField, 1, 2, 3, 4, 5, 6, 7);
    }

    @DontInline
    @ForceCompile(compLevel = C1)
      private static float test46_helper(FloatPoint fp1, FloatPoint fp2, Point p1, FloatPoint fp3, FloatPoint fp4, Point p2, FloatPoint fp5, int a1, int a2, int a3, int a4, int a5, int a6, int a7) {
        return p1.x + p1.y +
               p2.x + p2.y +
               fp1.x + fp1.y +
               fp2.x + fp2.y +
               fp3.x + fp3.y +
               fp4.x + fp4.y +
               fp5.x + fp5.y +
               a1 + a2 + a3 + a4 + a5 + a6 + a7;
    }

    @DontCompile
    public void test46_verifier(boolean warmup) {
        int count = warmup ? 1 : 2;
        for (int i=0; i<count; i++) { // need a loop to test inline cache
            float result = test46();
            float n = test46_helper(floatPointField, floatPointField, pointField, floatPointField, floatPointField, pointField, floatPointField, 1, 2, 3, 4, 5, 6, 7);
            Asserts.assertEQ(result, n);
        }
    }

    static class MyRuntimeException extends RuntimeException {
        MyRuntimeException(String s) {
            super(s);
        }
    }

    static void checkStackTrace(Throwable t, String... methodNames) {
        StackTraceElement[] trace = t.getStackTrace();
        for (int i=0; i<methodNames.length; i++) {
            if (!methodNames[i].equals(trace[i].getMethodName())) {
                String error = "Unexpected stack trace: level " + i + " should be " + methodNames[i];
                System.out.println(error);
                t.printStackTrace(System.out);
                throw new RuntimeException(error, t);
            }
        }
    }
    //*

    // C2->C1 invokestatic, make sure stack walking works (with static variable)
    @Test(compLevel = C2)
    public void test47(int n) {
        try {
            test47_helper(floatPointField, 1, 2, 3, 4, 5);
            test47_value = 666;
        } catch (MyRuntimeException e) {
            // expected;
        }
        test47_value = n;
    }

    @DontInline
    @ForceCompile(compLevel = C1)
    private static float test47_helper(FloatPoint fp, int a1, int a2, int a3, int a4, int a5) {
        test47_thrower();
        return 0.0f;
    }

    @DontInline @DontCompile
    private static void test47_thrower() {
        MyRuntimeException e = new MyRuntimeException("This exception should have been caught!");
        checkStackTrace(e, "test47_thrower", "test47_helper", "test47", "test47_verifier");
        throw e;
    }

    static int test47_value = 999;

    @DontCompile
    public void test47_verifier(boolean warmup) {
        int count = warmup ? 1 : 5;
        for (int i=0; i<count; i++) { // need a loop to test inline cache
            test47_value = 777 + i;
            test47(i);
            Asserts.assertEQ(test47_value, i);
        }
    }

    // C2->C1 invokestatic, make sure stack walking works (with returned value)
    @Test(compLevel = C2)
    public int test48(int n) {
        try {
            test48_helper(floatPointField, 1, 2, 3, 4, 5);
            return 666;
        } catch (MyRuntimeException e) {
            // expected;
        }
        return n;
    }

    @DontInline
    @ForceCompile(compLevel = C1)
    private static float test48_helper(FloatPoint fp, int a1, int a2, int a3, int a4, int a5) {
        test48_thrower();
        return 0.0f;
    }

    @DontInline @DontCompile
    private static void test48_thrower() {
        MyRuntimeException e = new MyRuntimeException("This exception should have been caught!");
        checkStackTrace(e, "test48_thrower", "test48_helper", "test48", "test48_verifier");
        throw e;
    }

    @DontCompile
    public void test48_verifier(boolean warmup) {
        int count = warmup ? 1 : 5;
        for (int i=0; i<count; i++) { // need a loop to test inline cache
            int n = test48(i);
            Asserts.assertEQ(n, i);
        }
    }

    // C2->interpreter invokestatic, make sure stack walking works (same as test 48, but with stack extension/repair)
    // (this is the baseline for test50 --
    // the only difference is: test49_helper is interpreted but test50_helper is compiled by C1).
    @Test(compLevel = C2)
    public int test49(int n) {
        try {
            test49_helper(floatPointField, 1, 2, 3, 4, 5, 6);
            return 666;
        } catch (MyRuntimeException e) {
            // expected;
        }
        return n;
    }

    @DontInline @DontCompile
    private static float test49_helper(FloatPoint fp, int a1, int a2, int a3, int a4, int a5, int a6) {
        test49_thrower();
        return 0.0f;
    }

    @DontInline @DontCompile
    private static void test49_thrower() {
        MyRuntimeException e = new MyRuntimeException("This exception should have been caught!");
        checkStackTrace(e, "test49_thrower", "test49_helper", "test49", "test49_verifier");
        throw e;
    }

    @DontCompile
    public void test49_verifier(boolean warmup) {
        int count = warmup ? 1 : 5;
        for (int i=0; i<count; i++) { // need a loop to test inline cache
            int n = test49(i);
            Asserts.assertEQ(n, i);
        }
    }

    // C2->C1 invokestatic, make sure stack walking works (same as test 48, but with stack extension/repair)
    @Test(compLevel = C2)
    public int test50(int n) {
        try {
            test50_helper(floatPointField, 1, 2, 3, 4, 5, 6);
            return 666;
        } catch (MyRuntimeException e) {
            // expected;
        }
        return n;
    }

    @DontInline
    @ForceCompile(compLevel = C1)
    private static float test50_helper(FloatPoint fp, int a1, int a2, int a3, int a4, int a5, int a6) {
        test50_thrower();
        return 0.0f;
    }

    @DontInline @DontCompile
    private static void test50_thrower() {
        MyRuntimeException e = new MyRuntimeException("This exception should have been caught!");
        checkStackTrace(e, "test50_thrower", "test50_helper", "test50", "test50_verifier");
        throw e;
    }

    @DontCompile
    public void test50_verifier(boolean warmup) {
        int count = warmup ? 1 : 5;
        for (int i=0; i<count; i++) { // need a loop to test inline cache
            int n = test50(i);
            Asserts.assertEQ(n, i);
        }
    }
}
