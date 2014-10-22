/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.r.nodes.builtin.base;

import com.oracle.truffle.api.dsl.CreateCast;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.utilities.ValueProfile;
import com.oracle.truffle.r.nodes.RNode;
import com.oracle.truffle.r.nodes.access.ConstantNode;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.unary.ConvertIntFactory;
import com.oracle.truffle.r.runtime.RBuiltin;
import com.oracle.truffle.r.runtime.RError;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.data.RDataFactory;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.model.RAbstractVector;

import java.util.Arrays;

import static com.oracle.truffle.api.CompilerDirectives.SlowPath;
import static com.oracle.truffle.r.runtime.RBuiltinKind.INTERNAL;

@RBuiltin(name = "vector", kind = INTERNAL, parameterNames = {"mode", "length"})
public abstract class Vector extends RBuiltinNode {
    private final ValueProfile modeProfile = ValueProfile.createIdentityProfile();

    @Override
    public RNode[] getParameterValues() {
        // mode = "logical", length = 0
        return new RNode[]{ConstantNode.create(RType.Logical.getName()), ConstantNode.create(0)};
    }

    @CreateCast("arguments")
    protected RNode[] castLength(RNode[] arguments) {
        // length is at index 1
        arguments[1] = ConvertIntFactory.create(arguments[1]);
        return arguments;
    }

    @Specialization
    @SlowPath
    protected RAbstractVector vector(String mode, int length) {
        controlVisibility();
        switch (modeProfile.profile(mode)) {
            case "character":
                return RDataFactory.createStringVector(length);
            case "logical":
                return RDataFactory.createLogicalVector(length);
            case "numeric":
            case "double":
                return RDataFactory.createDoubleVector(length);
            case "integer":
                return RDataFactory.createIntVector(length);
            case "list":
                Object[] data = new Object[length];
                Arrays.fill(data, RNull.instance);
                return RDataFactory.createList(data);
            default:
                throw RError.error(getEncapsulatingSourceSection(), RError.Message.CANNOT_MAKE_VECTOR_OF_MODE, mode);
        }
    }

}
