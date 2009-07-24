/*
 * Copyright (c) 2007 Sun Microsystems, Inc.  All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product
 * that is described in this document. In particular, and without limitation, these intellectual property
 * rights may include one or more of the U.S. patents listed at http://www.sun.com/patents and one or
 * more additional patents or pending patent applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun
 * Microsystems, Inc. standard license agreement and applicable provisions of the FAR and its
 * supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or
 * registered trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks
 * are used under license and are trademarks or registered trademarks of SPARC International, Inc. in the
 * U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open
 * Company, Ltd.
 */
/*
 * Copyright (c) 2007 Sun Microsystems, Inc. All rights reserved. Use is subject to license terms.
 */
package test.com.sun.max.vm.testrun.some;

import test.com.sun.max.vm.testrun.*;

import com.sun.max.annotate.*;
import com.sun.max.vm.*;


public class JavaTesterRunScheme extends AbstractTester {

    public JavaTesterRunScheme(VMConfiguration vmConfiguration) {
        super(vmConfiguration);
    }

    @PROTOTYPE_ONLY
    @Override
    public Class<?>[] getClassList() {
        return classList;
    }

    @PROTOTYPE_ONLY
// GENERATED TEST RUNS
    private static final Class<?>[] classList = {
        test.bytecode.BC_getstatic_i.class
    };
    @Override
    public void runTests() {
        total = testEnd - testStart;
        testNum = testStart;
        while (testNum < testEnd) {
            switch(testNum) {
                case 0:
                    JavaTesterTests.test_bytecode_BC_getstatic_i();
            }
        }
        reportPassed(passed, total);
    }
// END GENERATED TEST RUNS
}
