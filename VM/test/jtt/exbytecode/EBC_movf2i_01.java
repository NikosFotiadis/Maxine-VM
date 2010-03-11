/*
 * Copyright (c) 2007 Sun Microsystems, Inc. All rights reserved.
 *
 * Sun Microsystems, Inc. has intellectual property rights relating to technology embodied in the product that is
 * described in this document. In particular, and without limitation, these intellectual property rights may include one
 * or more of the U.S. patents listed at http://www.sun.com/patents and one or more additional patents or pending patent
 * applications in the U.S. and in other countries.
 *
 * U.S. Government Rights - Commercial software. Government users are subject to the Sun Microsystems, Inc. standard
 * license agreement and applicable provisions of the FAR and its supplements.
 *
 * Use is subject to license terms. Sun, Sun Microsystems, the Sun logo, Java and Solaris are trademarks or registered
 * trademarks of Sun Microsystems, Inc. in the U.S. and other countries. All SPARC trademarks are used under license and
 * are trademarks or registered trademarks of SPARC International, Inc. in the U.S. and other countries.
 *
 * UNIX is a registered trademark in the U.S. and other countries, exclusively licensed through X/Open Company, Ltd.
 */
package jtt.exbytecode;

import com.sun.max.vm.compiler.builtin.*;

// register -> register

/*
 * @Harness: java
 * @Runs: `java.lang.Float.NaN = 0x7fc00000; 1.0f = 0x3f800000; -1.0f = -1082130432; 473000.0f = 0x48e6f500
 */

public class EBC_movf2i_01 {

    public static int test(float arg) {
        return SpecialBuiltin.floatToInt(arg);
    }
}
