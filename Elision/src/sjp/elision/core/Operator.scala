/* Copyright (c) 2012 by Stacy Prowell (sprowell@gmail.com).
 * All rights reserved.  http://stacyprowell.com
 *       _ _     _
 *   ___| (_)___(_) ___  _ __
 *  / _ \ | / __| |/ _ \| '_ \
 * |  __/ | \__ \ | (_) | | | |
 *  \___|_|_|___/_|\___/|_| |_|
 */
package sjp.elision.core

import scala.collection.immutable.HashMap

/**
 * A type for all operators.
 */
object OPTYPE extends RootType {
  val theType = TypeUniverse
  
  override def toString = "OPTYPE"
}

/**
 * Encapsulate an operator.
 */
case class Operator(name: String) extends BasicAtom {
  val theType = OPTYPE

  def tryMatchWithoutTypes(subject: BasicAtom, binds: Bindings) =
    subject match {
      case Operator(oname) if name == oname => Match(binds)
      case _ => Fail("Operators do not match.", this, subject)
    }

  def rewrite(binds: Bindings) = (this, false)

  override def toString = toESymbol(name)
}

