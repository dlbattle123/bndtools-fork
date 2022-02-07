/*******************************************************************************
 * Copyright (c) Contributors to the Eclipse Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 *******************************************************************************/

package org.bndtools.test.assertj.eclipse.jdt.core.compiler.imarker;

import org.assertj.core.api.InstanceOfAssertFactory;
import org.eclipse.core.resources.IMarker;

public class JavaProblemMarkerAssert extends AbstractJavaProblemMarkerAssert<JavaProblemMarkerAssert, IMarker> {

	public static final InstanceOfAssertFactory<IMarker, JavaProblemMarkerAssert> JAVA_PROBLEM = new InstanceOfAssertFactory<>(
		IMarker.class, JavaProblemMarkerAssert::assertThat);

	public JavaProblemMarkerAssert(IMarker actual) {
		super(actual, JavaProblemMarkerAssert.class);
	}

	public static JavaProblemMarkerAssert assertThat(IMarker actual) {
		return new JavaProblemMarkerAssert(actual);
	}
}
