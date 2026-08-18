/*
 *
 * Copyright (c) Innovar Healthcare. All rights reserved.
 *
 * https://www.innovarhealthcare.com
 *
 * The software in this package is published under the terms of the MPL license a copy of which has
 * been included with this distribution in the LICENSE.txt file.
 */

package com.mirth.connect.test;

/**
 * JUnit {@link org.junit.experimental.categories.Category} marker for slow tests.
 *
 * <p>Apply via {@code @Category(SlowTest.class)} at the class or method level.
 * Test classes / methods carrying this category may take noticeably longer than
 * the typical unit test (e.g., the 50-sample PBKDF2 timing-parity assertion in
 * {@link com.mirth.connect.server.controllers.AuthorizeUserTimingTest}). The
 * default {@code ant test} target still runs them; future fast pre-commit hooks
 * can exclude them via a {@code <batchtest>} category filter.
 *
 * <p>Intentionally empty — this is a marker interface only.
 */
public interface SlowTest {
}
