/*******************************************************************************
 * Copyright (c) 2009, 2014 Wind River Systems, Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Markus Schorn - initial API and implementation
 *******************************************************************************/
package org.eclipse.cdt.internal.index.tests;

import junit.framework.TestSuite;

public class IndexCPPBindingResolutionBugsSingleProjectFirstAST extends IndexCPPBindingResolutionBugs {
	public IndexCPPBindingResolutionBugsSingleProjectFirstAST() {
		setStrategy(new SinglePDOMTestFirstASTStrategy(true));
	}

	public static TestSuite suite() {
		return suite(IndexCPPBindingResolutionBugsSingleProjectFirstAST.class);
	}

	// Invalid tests for this strategy, they assume that the second file is already indexed.
	@Override public void test_208558() {}
	@Override public void test_176708_CCE() {}
	@Override public void testIsSameAnonymousType_193962() {}
	@Override public void testIsSameNestedAnonymousType_193962() {}
	// For some unknown reason this test is flaky for this strategy.
	@Override public void testTemplateArgumentResolution_450888() {}
}