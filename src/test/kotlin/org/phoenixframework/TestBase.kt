package org.phoenixframework

import io.mockk.MockKAnnotations
import org.junit.Before

abstract class TestBase {

  @Before
  open fun setup() {
    MockKAnnotations.init(this)
  }
}
