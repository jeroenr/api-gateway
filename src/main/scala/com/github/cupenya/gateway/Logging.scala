package com.github.cupenya.gateway

import org.slf4s.{Logging => SLF4SLogging}

trait Logging extends SLF4SLogging {
  @inline
  protected[this] lazy val logger = log
}
