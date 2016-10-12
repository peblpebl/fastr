/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.truffle.r.nodes.builtin.CastBuilder.Predef.instanceOf;
import static com.oracle.truffle.r.runtime.RVisibility.CUSTOM;
import static com.oracle.truffle.r.runtime.builtins.RBehavior.COMPLEX;
import static com.oracle.truffle.r.runtime.builtins.RBuiltinKind.INTERNAL;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.r.nodes.builtin.CastBuilder;
import com.oracle.truffle.r.nodes.builtin.RBuiltinNode;
import com.oracle.truffle.r.nodes.builtin.base.EvalNodeGen.EvalEnvCastNodeGen;
import com.oracle.truffle.r.nodes.builtin.base.GetFunctions.Get;
import com.oracle.truffle.r.nodes.builtin.base.GetFunctionsFactory.GetNodeGen;
import com.oracle.truffle.r.nodes.function.visibility.SetVisibilityNode;
import com.oracle.truffle.r.runtime.RCaller;
import com.oracle.truffle.r.runtime.RType;
import com.oracle.truffle.r.runtime.builtins.RBuiltin;
import com.oracle.truffle.r.runtime.context.RContext;
import com.oracle.truffle.r.runtime.data.RAttributeProfiles;
import com.oracle.truffle.r.runtime.data.RExpression;
import com.oracle.truffle.r.runtime.data.RLanguage;
import com.oracle.truffle.r.runtime.data.RList;
import com.oracle.truffle.r.runtime.data.RNull;
import com.oracle.truffle.r.runtime.data.RPairList;
import com.oracle.truffle.r.runtime.data.RSymbol;
import com.oracle.truffle.r.runtime.env.REnvironment;
import com.oracle.truffle.r.runtime.nodes.RBaseNode;

/**
 * Contains the {@code eval} {@code .Internal} implementation.
 */
@RBuiltin(name = "eval", visibility = CUSTOM, kind = INTERNAL, parameterNames = {"expr", "envir", "enclos"}, behavior = COMPLEX)
public abstract class Eval extends RBuiltinNode {

    /**
     * Eval takes two arguments that specify the environment where the expression should be
     * evaluated: 'envir', 'enclos'. These arguments are pre-processed by the means of default
     * values in the R stub function, but there is still several combinations of their possible
     * values that may make it into the internal code. This node handles these. See the
     * documentation of eval for more details.
     */
    abstract static class EvalEnvCast extends RBaseNode {

        public abstract REnvironment execute(Object env, Object enclos);

        private final RAttributeProfiles attributeProfiles = RAttributeProfiles.create();

        @Specialization
        protected REnvironment cast(@SuppressWarnings("unused") RNull env, @SuppressWarnings("unused") RNull enclos) {
            return REnvironment.baseEnv();
        }

        @Specialization
        protected REnvironment cast(REnvironment env, @SuppressWarnings("unused") RNull enclos) {
            return env;
        }

        @Specialization
        protected REnvironment cast(REnvironment env, @SuppressWarnings("unused") REnvironment enclos) {
            // from the doc: enclos is only relevant when envir is list or pairlist
            return env;
        }

        @Specialization
        protected REnvironment cast(@SuppressWarnings("unused") RNull env, REnvironment enclos) {
            // seems not to be documented, but GnuR works this way
            return enclos;
        }

        @Specialization
        protected REnvironment cast(RList list, REnvironment enclos) {
            return REnvironment.createFromList(attributeProfiles, list, enclos);
        }

        @Specialization
        protected REnvironment cast(RPairList list, REnvironment enclos) {
            return REnvironment.createFromList(attributeProfiles, list.toRList(), enclos);
        }

        @Specialization
        protected REnvironment cast(RList list, @SuppressWarnings("unused") RNull enclos) {
            // This can happen when envir is a list and enclos is explicitly set to NULL
            return REnvironment.createFromList(attributeProfiles, list, REnvironment.baseEnv());
        }

        @Specialization
        protected REnvironment cast(RPairList list, @SuppressWarnings("unused") RNull enclos) {
            // This can happen when envir is a pairlist and enclos is explicitly set to NULL
            return REnvironment.createFromList(attributeProfiles, list.toRList(), REnvironment.baseEnv());
        }
    }

    @Child private EvalEnvCast envCast = EvalEnvCastNodeGen.create();
    @Child private SetVisibilityNode visibility = SetVisibilityNode.create();

    @Override
    protected void createCasts(CastBuilder casts) {
        casts.arg("envir").allowNull().mustBe(instanceOf(REnvironment.class).or(instanceOf(RList.class)).or(instanceOf(RPairList.class)));
        casts.arg("enclos").allowNull().mustBe(REnvironment.class);
    }

    @Specialization
    protected Object doEval(VirtualFrame frame, RLanguage expr, Object envir, Object enclos) {
        RCaller rCaller = RCaller.create(frame, getOriginalCall());
        REnvironment environment = envCast.execute(envir, enclos);
        try {
            return RContext.getEngine().eval(expr, environment, rCaller);
        } finally {
            visibility.executeAfterCall(frame, rCaller);
        }
    }

    @Specialization
    protected Object doEval(VirtualFrame frame, RExpression expr, Object envir, Object enclos) {
        RCaller rCaller = RCaller.create(frame, getOriginalCall());
        REnvironment environment = envCast.execute(envir, enclos);
        try {
            return RContext.getEngine().eval(expr, environment, rCaller);
        } finally {
            visibility.executeAfterCall(frame, rCaller);
        }
    }

    protected static Get createGet() {
        return GetNodeGen.create(null);
    }

    @Specialization
    protected Object doEval(VirtualFrame frame, RSymbol expr, Object envir, Object enclos,
                    @Cached("createGet()") Get get) {
        REnvironment environment = envCast.execute(envir, enclos);

        try {
            // no need to do the full eval for symbols: just do the lookup
            return get.execute(frame, expr.getName(), environment, RType.Any.getName(), true);
        } finally {
            visibility.execute(frame, true);
        }
    }

    @Fallback
    protected Object doEval(VirtualFrame frame, Object expr, Object envir, Object enclos) {
        // just return value
        envCast.execute(envir, enclos);
        visibility.execute(frame, true);
        return expr;
    }
}