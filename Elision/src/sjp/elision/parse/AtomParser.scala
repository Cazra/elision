/*======================================================================
 *       _ _     _
 *   ___| (_)___(_) ___  _ __
 *  / _ \ | / __| |/ _ \| '_ \
 * |  __/ | \__ \ | (_) | | | |
 *  \___|_|_|___/_|\___/|_| |_|
 * The Elision Term Rewriter
 * 
 * Copyright (c) 2012 by Stacy Prowell (sprowell@gmail.com)
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met: 
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer. 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution. 
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
======================================================================*/
package sjp.elision.parse

import java.nio.charset.Charset
import org.parboiled.scala.{ ANY => PANY }
import org.parboiled.scala._
import org.parboiled.errors.{ ParseError, ErrorUtils, ParsingException }
import scala.collection.mutable.LinkedList
import sjp.elision.core._
import sjp.elision.core.{ ANY => EANY }

/**
 * Provide abstract syntax tree nodes used during parsing, along with any
 * supporting functions.
 */
object AtomParser {
  
  /**
   * Accumulate a character into a string.  The character may actually be an
   * escape sequence that will be interpreted here.
   * 
   * See `toEString` for the reverse conversion.
   * 
   * The following escapes are interpreted.
   * {{{
   * \"  -> double quotation mark
   * \`  -> backtick
   * \\  -> backslash
   * \n  -> newline
   * \t  -> tab
   * \r  -> carriage return
   * }}}
   * 
   * @param front	The string to get the new character appended at the end.
   * @param last	The new character, possibly an escape to interpret.
   * @return	The new string.
   */
  def append(front: StringBuilder, last: String) = {
    // Interpret the last part.  If it is an escape, then convert it to a
    // character first.
    front.append(last match {
      case """\"""" => "\""
      case """\`""" => "`"
      case """\\""" => "\\"
      case """\n""" => "\n"
      case """\t""" => "\t"
      case """\r""" => "\r"
      case _ => last
    })
  }
  
  /**
   * Given a list of strings, each of which represents a single character
   * (possibly as an escape), concatenate them into a single string.
   * 
   * @param chars	The list of characters.
   * @return	The composed string.
   */
  def construct(chars: List[String]) = {
    chars.foldLeft(new StringBuilder())(append(_,_)).toString
  }
	
	//======================================================================
	// Definitions for an abstract syntax tree for atoms.
  //----------------------------------------------------------------------
  // Here are all the myriad classes used to capture parts as they are 
  // parsed.  The basic idea is that each thing represented by an AstNode
  // must be interpretable as a BasicAtom, and this is the job of the
  // interpret method.
	//======================================================================
	
	/**
	 * An abstract syntax tree node resulting from parsing an atom.
	 */
	sealed abstract class AstNode {
	  /**
	   * Interpret this abstract syntax tree to generate an atom.
	   * @return	The generated atom.
	   */
	  def interpret: BasicAtom
	}
	
	//----------------------------------------------------------------------
	// Type nodes.
	//----------------------------------------------------------------------
	
	/**
	 * A node representing a simple type, which must be a root type.
	 * @param TYPE	The type.
	 */
	case class SimpleTypeNode(TYPE: RootType) extends AstNode {
	  def interpret = TYPE
	}
	
	/**
	 * A node representing the unique type universe.
	 */
	case class TypeUniverseNode() extends AstNode {
	  def interpret = TypeUniverse
	}
	
	//----------------------------------------------------------------------
	// Operator application.
	//----------------------------------------------------------------------
	
	/**
	 * A node representing the application of an operator to an argument.
	 * @param context	The context.
	 * @param op			The operator.
	 * @param arg			The argument.
	 */
	case class ApplicationNode(context: Context, op: AstNode, arg: AstNode)
	extends AstNode {
	  def interpret = {
	    // If the operator is a naked symbol, we try to interpret it as an
	    // operator.  Otherwise we just interpret it.
	    val atom = op match {
	      case NakedSymbolNode(name) => context.operatorLibrary(name)
	      case SymbolLiteralNode(None, name) => context.operatorLibrary(name)
	      case _ => op.interpret
	    }
	    Apply(atom, arg.interpret)
	  }
	}
	
	/**
	 * A node representing a "naked" operator.
	 * @param str	The operator name.
	 * @param lib	The operator library that will get the operator.
	 */
	case class OperatorNode(str: String, lib: OperatorLibrary) extends AstNode {
	  def interpret = lib(str)
	}
	
	/**
	 * A node representing a lambda.
	 * @param lvar	The lambda variable.
	 * @param body	The lambda body.
	 */
	case class LambdaNode(lvar: VariableNode, body: AstNode) extends AstNode {
	  def interpret = Lambda(lvar.interpret, body.interpret)
	}
	
	/**
	 * An abstract syntax tree node holding a simple list of atoms.
	 * 
	 * @param props	The properties of the list.
	 * @param list	The actual list of atom nodes.
	 */
	case class AtomSeqNode(props: AlgPropNode, list: List[AstNode])
	extends AstNode {
	  /**
	   * Properties of this list, if known.
	   */
	  def interpret =
	    AtomSeq(props.interpret, list.toIndexedSeq[AstNode] map (_.interpret))
	}
	
	//----------------------------------------------------------------------
	// Operator definition.
	//----------------------------------------------------------------------
	
	/** A property node. */
	sealed abstract class PropertyNode
	/** Associative property. */
	case class AssociativeNode(atom:AstNode) extends PropertyNode
	/** Commutative property. */
	case class CommutativeNode(atom:AstNode) extends PropertyNode
	/** Idempotent property. */
	case class IdempotentNode(atom:AstNode) extends PropertyNode
	/** Absorber property. */
	case class AbsorberNode(atom:AstNode) extends PropertyNode
	/** Identity property. */
	case class IdentityNode(atom:AstNode) extends PropertyNode
	
	/** A true node for fast access. */
	case object TrueNode extends AstNode {
	  def interpret = Literal.TRUE
	}
	
	/** A false node for fast access. */
	case object FalseNode extends AstNode {
	  def interpret = Literal.FALSE
	}
	
	/**
	 * A data structure holding operator properties as they are discovered during
	 * the parse.
	 */
	class AlgPropNode(props: List[PropertyNode]) extends AstNode {
	  /** An identity, if any. */
	  var withIdentity: Option[AstNode] = None
	  /** An absorber, if any. */
	  var withAbsorber: Option[AstNode] = None
	  /** True iff this operator is idempotent. */
	  var isIdempotent: Option[AstNode] = None
	  /** True iff this operator is commutative. */ 
	  var isCommutative: Option[AstNode] = None
	  /** True iff this operator is associative. */
	  var isAssociative: Option[AstNode] = None
	  
	  // Process any properties we were given.
	  for (prop <- props) prop match {
	    case AssociativeNode(an) => isAssociative = Some(an)
	    case CommutativeNode(cn) => isCommutative = Some(cn)
	    case IdempotentNode(in) => isIdempotent = Some(in)
	    case AbsorberNode(ab) => withAbsorber = Some(ab)
	    case IdentityNode(id) => withIdentity = Some(id)
	  }
	  
	  /** Print this properties object as a string. */
	  override def toString = interpret.toString
	  
	  private def _interpret(atom: Option[AstNode]) = atom match {
	    case None => None
	    case Some(real) => Some(real.interpret)
	  }
	  
	  /**
	   * Convert this into an operator properties instance.
	   * @return	The operator properties object.
	   */
	  def interpret = AlgProp(_interpret(isAssociative),
	      _interpret(isCommutative), _interpret(isIdempotent),
	      _interpret(withAbsorber), _interpret(withIdentity))
	}
	
	object AlgPropNode {
	  def apply() = new AlgPropNode(List())
	  def apply(list: List[PropertyNode]) = new AlgPropNode(list)
	}
	
	/**
	 * Represent an operator prototype.
	 * @param name			The operator name.
	 * @param pars			The formal parameter.
	 * @param typ				The type.
	 * @param guards		The guards, if any.
	 */
	class OperatorPrototypeNode(
	    val name: String,
	    val pars: List[VariableNode],
	    val typ: Option[AstNode],
	    val guards: List[AstNode]) {
	  def interpret = OperatorPrototype(
	      name,
	      pars.toIndexedSeq[VariableNode].map(_.interpret),
	      typ match {
	        case Some(actual) => actual.interpret
	        case None => EANY
	      },
	      guards.map(_.interpret))
	}
	
	/**
	 * The common root class for all operator definition nodes.
	 */
	sealed abstract class OperatorDefinitionNode extends AstNode {
	  def interpret: OperatorDefinition
	}
	
	/**
	 * Represent a symbolic operator definition.
	 * @param opn		The prototype node.
	 * @param prop	The properties.
	 */
	case class SymbolicOperatorDefinitionNode(
	    opn: OperatorPrototypeNode,
	    prop: AlgPropNode) extends OperatorDefinitionNode {
	  def interpret = SymbolicOperatorDefinition(opn.interpret, prop.interpret)
	}
	
	/**
	 * Represent an immediate operator definition.
	 * @param opn		The prototype node.
	 * @param prop	The body.
	 */
	case class ImmediateOperatorDefinitionNode(
	    opn: OperatorPrototypeNode,
	    body: AstNode) extends OperatorDefinitionNode {
	  def interpret = ImmediateOperatorDefinition(opn.interpret, body.interpret)
	}
	
	//----------------------------------------------------------------------
	// Object and binding nodes.
	//----------------------------------------------------------------------

	/**
	 * Represent a bindings atom.
	 * @param map	The bindings.
	 */
	case class BindingsNode(map: List[(NakedSymbolNode,AstNode)]) extends AstNode {
	  def interpret = {
	    var binds = Bindings()
	    for ((str,node) <- map) {
	      binds += (str.str -> node.interpret)
	    }
	    BindingsAtom(binds)
	  }
	}
	
	//----------------------------------------------------------------------
	// Map pair and case nodes.
	//----------------------------------------------------------------------
	
	/**
	 * Represent a map pair atom.
	 * 
	 * @param left	The left atom.
	 * @param right	THe right atom.
	 */
	case class MapPairNode(left: AstNode, right: AstNode) extends AstNode {
	  def interpret = MapPair(left.interpret, right.interpret)
	}
	
	//----------------------------------------------------------------------
	// Matching nodes.
	//----------------------------------------------------------------------
	
	/**
	 * A node denoting a pattern to match when applied on the left.
	 * 
	 * @param pat	The pattern.
	 * @return	A match atom.
	 */
	case class MatchNode(pat: AstNode) extends AstNode {
	  def interpret = {
	    MatchAtom(pat.interpret)
	  }
	}
	
	//----------------------------------------------------------------------
	// Symbol nodes.
	//----------------------------------------------------------------------
	
	/**
	 * A node representing a "naked" symbol: a symbol whose type is the type
	 * ANY.
	 * @param str	The symbol text.
	 */
	case class NakedSymbolNode(str: String) extends AstNode {
	  def interpret = SymbolLiteral(SYMBOL, Symbol(str))
	}
	
	/**
	 * A node representing a symbol.
	 * @param typ		The type.
	 * @param name	The symbol text.
	 */
	case class SymbolNode(typ: AstNode, name: String) extends AstNode {
	  def interpret = Literal(typ.interpret, name)
	}
	
	//----------------------------------------------------------------------
	// Variable nodes.
	//----------------------------------------------------------------------
	
	/**
	 * A node representing a variable reference.
	 * @param typ			The type.
	 * @param name		The variable name.
	 * @param guard		The variable's guard.
	 * @param labels	Labels associated with the variable.
	 */
	class VariableNode(val typ: AstNode, val name: String,
	    val grd: Option[AstNode], val labels: Set[String]) extends AstNode {
	  def interpret: Variable = grd match {
	    case None => Variable(typ.interpret, name, Literal.TRUE, labels)
	    case Some(guard) => Variable(typ.interpret, name, guard.interpret, labels)
	  }
	}
	object VariableNode {
	  def apply(typ: AstNode, name: String, grd: Option[AstNode],
	    labels: Set[String]) = new VariableNode(typ, name, grd, labels)
	}
	
	/**
	 * A node representing a metavariable reference.
	 * 
	 * @param vx	An ordinary variable node to promote.
	 */
	case class MetaVariableNode(vx: VariableNode)
	extends VariableNode(vx.typ, vx.name, vx.grd, vx.labels) {
	  override def interpret: MetaVariable = grd match {
	    case None =>
	      MetaVariable(vx.typ.interpret, vx.name, Literal.TRUE, labels)
	    case Some(guard) =>
	      MetaVariable(vx.typ.interpret, vx.name, guard.interpret, labels)
	  }
	}
	
	//----------------------------------------------------------------------
	// Rule nodes.
	//----------------------------------------------------------------------
	
	/**
	 * A node representing the declaration of zero or more rulesets.
	 * 
	 * @param context		The context to get the rulesets.
	 * @param rulesets	The ruleset names.
	 */
	case class RulesetDeclarationNode(context: Context,
	    rulesets: List[NakedSymbolNode]) extends AstNode {
	  require(context != null, "context")
	  require(rulesets != null, "rulsets")
	  def interpret = {
	    rulesets.foreach(nsn => context.declareRuleset(nsn.str))
	    Literal.TRUE
	  }
	}
	
	/**
	 * A node representing a rewrite rule.
	 * @param decls			Optional pattern variable declarations.
	 * @param pattern		The pattern to match.
	 * @param rewrite		The rewrite to apply when the pattern matches.
	 * @param guards		The list of guards.
	 * @param rulesets	The optional list of rulesets.
	 * @param level			The optional cache level.
	 */
	case class RuleNode(
	    decls: Option[List[VariableNode]],
	    mappair: MapPairNode,
	    guards: List[AstNode],
	    rulesets: Option[List[NakedSymbolNode]],
	    level: Option[UnsignedIntegerNode]) extends AstNode {
	  def interpret = {
	    // Get the rulesets as a list of strings.
	    val rs = rulesets match {
	      case None => Set[String]()
	      case Some(list) => list.map(_.str).toSet
	    }
	    // Get the cache level.  The default is zero.
	    val cl = level match {
	      case None => 0
	      case Some(value) => value.asInt.toInt
	    }
	    // Make the rule.
	    RewriteRule(
	        mappair.left.interpret,
	        mappair.right.interpret,
	        guards.map(_.interpret),
	        rs,
	        cl)
	  }
	}
	
	/**
	 * A node representing a ruleset strategy.
	 * 
	 * @param context		A context to supply the rules.
	 * @param rulesets	Names of the rulesets to use.
	 */
	case class RulesetsNode(context: Context, rulesets: List[NakedSymbolNode])
	extends AstNode {
	  def interpret = RulesetStrategy(context, rulesets.map(_.str))
	}
	
	/**
	 * A node representing the map strategy.
	 * 
	 * @param labels	The labels to receive the mapping.
	 * @param exclude	Labels to exclude from the mapping.
	 * @param atom		The atom to map.
	 */
	case class MapNode(include: List[NakedSymbolNode],
	    exclude: List[NakedSymbolNode], atom: AstNode) extends AstNode {
	  def interpret = MapStrategy(
	      include.map(_.str).toSet, exclude.map(_.str).toSet, atom.interpret)
	}
	
	/**
	 * A node representing the right map strategy.
	 * 
	 * @param atom		The atom to map.
	 */
	case class RMapNode(atom: AstNode) extends AstNode {
	  def interpret = RMapStrategy(atom.interpret)
	}
	
	//----------------------------------------------------------------------
	// Literal nodes - that is, nodes that hold literal values.
	//----------------------------------------------------------------------
	
	/**
	 * A node representing a symbol literal.
	 * @param typ		The type.
	 * @param sym		The symbol text.
	 */
	case class SymbolLiteralNode(typ: Option[AstNode], sym: String) extends AstNode {
	  def interpret: BasicAtom = {
	    // If there is no type, or the type is the type universe, then check the
	    // symbol to see if it is a known root type.  This is also where the
	    // symbol _ gets turned into ANY.
	    if (typ == None || typ.get.isInstanceOf[TypeUniverseNode]) {
	      val lookup = (if (sym == "_") "ANY" else sym)
	      NamedRootType.get(lookup) match {
	        case Some(nrt) => return nrt
	        case _ =>
	      }
	    }
	    
	    // There are interesting "untyped" cases.  Without type, true and false
	    // should be made Booleans, and Nothing should have type ANY.
	    if (typ == None) sym match {
	      case "true" => return Literal.TRUE
	      case "false" => return Literal.FALSE
	      case _ => Literal(SYMBOL, Symbol(sym))
	    } else {
	      typ.get.interpret match {
	        case BOOLEAN if sym == "true" => return Literal.TRUE
	        case BOOLEAN if sym == "false" => return Literal.FALSE
	        case t:Any => return Literal(t, Symbol(sym))
	      }
	    }
	  }
	}
	
	/**
	 * A node representing a string literal.
	 * @param typ		The type.
	 * @param str		The string text.
	 */
	case class StringLiteralNode(typ: AstNode, str: String) extends AstNode {
	  def interpret = Literal(typ.interpret, str)
	}
	
	//----------------------------------------------------------------------
	// Numeric literal value nodes.
	//----------------------------------------------------------------------
	
	/**
	 * Root of all numeric abstract syntax tree nodes.
	 */
	abstract class NumberNode extends AstNode {
	  /**
	   * Make a new version of this node, with the specified type.
	   * @param newtyp		The new type.
	   * @return	A new node with the given type.
	   */
	  def retype(newtyp: AstNode): NumberNode
	}
	
	/**
	 * An abstract syntax tree node holding a numeric value.
	 * @param sign			True if positive, false if negative.
	 * @param integer		The integer portion of the number.
	 * @param fraction	The fractional portion of the number, if any.
	 * @param exponent	The exponent, if any.
	 * @param typ				The overriding type.  Otherwise it is inferred.
	 */
	object NumberNode {
	  /**
	   * Make a new node of the appropriate form to hold a number.
	   * 
	   * The number is constructed as follows.
	   * <code>[sign] [integer].[fraction] e [exponent]</code>
	   * The radix for the result is taken from the integer portion.
	   * 
	   * @param sign			If false, the number is negative.  Otherwise positive.
	   * @param integer		The integer portion of the number.
	   * @param fraction	The fractional portion of the number.  By default, none.
	   * @param exponent	The exponent.  By default, zero.
	   * @param typ				The type for the number.
	   * @return	The new number node.
	   */
	  def apply(sign: Option[Boolean], integer: UnsignedIntegerNode,
	      fraction: Option[UnsignedIntegerNode],
	      exponent: Option[SignedIntegerNode],
	      typ: Option[AstNode] = None): NumberNode = {
	    // Make the sign concrete.
	    val theSign = sign.getOrElse(true)
	    // If there is neither fraction nor exponent, then this is just an integer.
	    // Otherwise it is a float.
	    if (fraction.isDefined || exponent.isDefined) {
	      // This is a float.  Get the parts.
	      val fracpart = (if (fraction.isDefined) fraction.get.digits else "")
	      val exppart = exponent.getOrElse(SignedIntegerNode.Zero)
	      FloatNode(theSign, integer.digits, fracpart, integer.radix, exppart)
	    } else {
	      SignedIntegerNode(theSign, integer.digits, integer.radix)
	    }
	  }
	}
	
	/**
	 * An abstract syntax tree node holding an unsigned integer.  The integer value
	 * should be regarded as positive.
	 * @param digits	The digits of the number.
	 * @param radix		The radix.
	 * @param typ			The type.  If not specified, INTEGER is used.
	 */
	case class UnsignedIntegerNode(digits: String, radix: Int,
	    typ: AstNode = SimpleTypeNode(INTEGER)) extends NumberNode {
	  def interpret = Literal(typ.interpret, asInt)
	  /** Get the unsigned integer as a positive native integer value. */
	  lazy val asInt = BigInt(digits, radix)
	  def retype(newtyp: AstNode) = UnsignedIntegerNode(digits, radix, newtyp)
	}
	
	/**
	 * An abstract syntax tree node holding a signed integer.
	 * @param sign		If true, positive, and if false, negative.
	 * @param digits	The digits of the number.
	 * @param radix		The radix.
	 * @param typ			The type.  If not specified, INTEGER is used.
	 */
	case class SignedIntegerNode(sign: Boolean, digits: String, radix: Int,
	    typ: AstNode = SimpleTypeNode(INTEGER)) extends NumberNode {
	  def interpret = Literal(typ.interpret, asInt)
	  lazy val asInt = if (sign) asUInt else -asUInt
	  lazy val asUInt = BigInt(digits, radix)
	  def retype(newtyp: AstNode) = SignedIntegerNode(sign, digits, radix, newtyp)
	}
	
	/**
	 * Provide other methods to construct a signed integer.
	 */
	object SignedIntegerNode {
	  /** Zero. */
	  val Zero = SignedIntegerNode(true, "0", 10)
	  /** One. */
	  val One = SignedIntegerNode(true, "1", 10)
	  
	  /**
	   * Create a new signed integer from the given unsigned integer.  The radix
	   * is the same as the signed integer.
	   * @param sign		If true or None, positive.  If false, negative.
	   * @param integer	The unsigned integer value.
	   * @param typ			The type.
	   */
	  def apply(sign: Option[Boolean], integer: UnsignedIntegerNode,
	      typ: AstNode): SignedIntegerNode =
	    sign match {
	      case Some(bool) => SignedIntegerNode(bool, integer.digits, integer.radix,
	          typ)
	      case None => SignedIntegerNode(true, integer.digits, integer.radix, typ)
	    }
	  
	  /**
	   * Create a new signed integer from the given unsigned integer.  The radix
	   * is the same as the signed integer.
	   * @param sign		If true or None, positive.  If false, negative.
	   * @param integer	The unsigned integer value.
	   */
	  def apply(sign: Option[Boolean],
	      integer: UnsignedIntegerNode): SignedIntegerNode =
	    SignedIntegerNode(sign, integer, SimpleTypeNode(INTEGER))
	}
	
	/**
	 * An abstract syntax tree node holding a floating point value.
	 * @param sign			True if positive, false if negative.
	 * @param integer		The integer portion's digits.
	 * @param fraction	The fractional portion's digits.
	 * @param radix			The radix for the integer and fraction.
	 * @param exp				The exponent.
	 * @param typ				The type.  If not specified, FLOAT is used.
	 */
	case class FloatNode(sign: Boolean, integer: String, fraction: String,
	    radix: Int, exp: SignedIntegerNode, typ: AstNode = SimpleTypeNode(FLOAT))
	    extends NumberNode {
	  // We need to modify the integer and fraction parts to create the proper
	  // significand.  This is done as follows.  If there are n digits in the
	  // fraction, then we need to subtract n from the exponent.  Now, to do the
	  // math we have to convert the exponent into an actual integer, then do the
	  // math, and then convert it back to the proper number in the correct radix.
	  // We do all that now.
	  
	  /**
	   * The normalized version of this float.  It is a pair consisting of the
	   * significand and exponent.  Note that the two may use different radices.
	   */
	  lazy val norm = {
	    // Correct the significand by adding the integer and fractional part
	    // together.  This looks odd, but remember that they are still strings.
	    val significand = integer + fraction
	    // Now get the exponent, and adjust it to account for the fractional part.
	    // Since the decimal moves right, we subtract from the original exponent.
	    var exponent = exp.asInt
	    exponent -= fraction.length
	    // The sign of the exponent might change, so we recompute the sign here.
	    // We separate the sign and the exponent, so we also need the unsigned
	    // version of the exponent.
	    val newsign = exponent >= 0
	    exponent = exponent.abs
	    // Construct the new exponent as a signed integer node.  We use the same
	    // radix as the significand.
	    val newexp = SignedIntegerNode(newsign,
	        exponent.toString(exp.radix), exp.radix)
	    // Done.  Return the significand and exponent.  Note these two might have
	    // different radix.
	    (SignedIntegerNode(sign, significand, radix), newexp)
	  }
	  
	  /**
	   * The simple triple representation of this float.  It consists of the
	   * significand, the exponent, and the preferred radix (from the significand).
	   */
	  lazy val asTriple = (norm._1.asInt, norm._2.asInt, radix)
	  
	  /**
	   * Compute the platform float representation of this number.  This is not
	   * very likely to be useful, but might be very good for debugging.
	   */
	  lazy val asFloat = norm._1.asInt * BigInt(radix).pow(norm._2.asInt.toInt)
	  
	  def interpret = Literal(typ.interpret, norm._1.asInt.toInt,
	      norm._2.asInt.toInt, radix)
	  
	  def retype(newtyp: AstNode) = FloatNode(sign, integer, fraction, radix, exp,
	      newtyp)
	}
}

//======================================================================
// Parse and build atoms.
//======================================================================

/**
 * A parser to parse a single atom.
 * 
 * @param context	The context for rulesets and operators.
 * @param trace 	If true, enable tracing.  Off by default.
 */
class AtomParser(val context: Context, val trace: Boolean = false)
extends Parser {
  import AtomParser._
  
  /** A parse result. */
  abstract sealed class Presult
  
  /**
   * The parse was successful.
   * 
   * @param nodes	The nodes parsed.
   */
  case class Success(nodes: List[AstNode]) extends Presult
  
  /**
   * The parse failed.
   * 
   * @param err	The reason for the parsing failure.
   */
  case class Failure(err: String) extends Presult
  
//  def parseAtoms(filename: String,
//      charset: Charset = Charset.forName("UTF8")): Presult = {
//    // Read all the text from the file.
//    var text = 
//    val fis = new java.io.FileInputStream(filename)
//    
//    // Get a parse runnger.
//    val tr = ReportingParseRunner(AtomSeq)
//    val parsingResult = tr.run()
//  }
  
  /**
   * Entry point to parse all atoms from the given string.
   * 
   * @param line	The string to parse.
   * @return	The parsing result.
   */
  def parseAtoms(line: String): Presult = {
    val tr =
      if (trace) TracingParseRunner(AtomSeq)
      else ReportingParseRunner(AtomSeq)
    val parsingResult = tr.run(line)
    parsingResult.result match {
      case Some(nodes) => Success(nodes)
      case None => Failure("Invalid MPL2 source:\n" +
              ErrorUtils.printParseErrors(parsingResult))
    }
  }

  //======================================================================
  // Parse and build atoms.
  //----------------------------------------------------------------------
  // The basic approach is the following.
  //
  // We parse a sequence of atoms (so we parse everything in a line) using
  // the AtomSeq rule.
  // 
  // Because we want the applicative dot (.) to bind with the least precedence,
  // we next parse it in Atom.
  //
  // Most atoms are parsed with FirstAtom, including allowing parenthesized
  // atoms (and thus matches and general application).
  //
  // For each "thing" processed, we create an instance of a subclass of
  // AstNode and return it.  This has an interpret method that reads the
  // AST to generate the concrete parsed "thing," whatever it should be.
  //======================================================================
  
  /**
   * Parse all the atoms that can be found in the input.
   */
  def AtomSeq = rule {
    zeroOrMore(Atom) ~ WS ~ EOI
  }.label("a sequence of atoms")
  
  def InfixOperatorL = rule {
    oneOrMore(anyOf("~-!@#%^&*+=|';?/><")) ~> {
      str => NakedSymbolNode(str)
    }
  }
  
  def InfixOperatorR = rule {
    oneOrMore(anyOf("~-!@#%^&*+=|';?/><")) ~ ":" ~> {
      str => NakedSymbolNode(str)
    }
  }
  
  def LApply = rule {
    // Handle the odd case of an infix operator.  Only certain symbols can
    // be used as infix operators.  Associate to the right.
    FirstAtom ~ WS ~ InfixOperatorL ~ WS ~ Atom ~~> (
        (larg: AstNode, op: NakedSymbolNode, rarg: AstNode) =>
        	ApplicationNode(context, op, AtomSeqNode(AlgPropNode(), List(larg, rarg))))
  }
  
  def RApply = rule {
    // Handle the odd case of an infix operator.  Only certain symbols can
    // be used as infix operators.  Associate to left.
    Atom ~ WS ~ InfixOperatorR ~ WS ~ FirstAtom ~~> (
        (larg: AstNode, op: NakedSymbolNode, rarg: AstNode) =>
        	ApplicationNode(context, op, AtomSeqNode(AlgPropNode(), List(larg, rarg))))
  }

  /**
   * Parse an atom.
   */
  def Atom: Rule1[AstNode] = rule {
    FirstAtom ~ WS ~ "-> " ~ FirstAtom ~~> (MapPairNode(_,_)) |
    //LApply |
    // Handle the special case of the general operator application.  These
    // bind to the right, so: f.g.h.7 denotes Apply(f,Apply(g,Apply(h,7))).
//    zeroOrMore(FirstAtom ~ WS ~ ". ") ~ FirstAtom ~~> (
//        (funlist: List[AstNode], lastarg: AstNode) =>
//          funlist.foldRight(lastarg)(ApplicationNode(context,_,_))) |
    FirstAtom ~ zeroOrMore(". " ~ FirstAtom) ~~> (
        (firstarg: AstNode, funlist: List[AstNode]) =>
          funlist.foldLeft(firstarg)(ApplicationNode(context,_,_)))
//    zeroOrMore(FirstAtom ~ WS ~ "-> ") ~ FirstAtom ~~> (
//        (maplist: List[AstNode], lastarg: AstNode) =>
//          maplist.foldRight(lastarg)(MapPairNode(_,_)))
//    FirstAtom ~ WS ~ ". " ~ Atom ~~> (ApplicationNode(context,_,_)) |
  }.label("an atom")
  
  /**
   * Parse an atom, with the exception of the general operator application.
   */
  def FirstAtom: Rule1[AstNode] = rule {
    WS ~ (
      // Handle parenthetical expressions.
      "( " ~ Atom ~ ") " |
      
      // Declare a ruleset.
      ParsedRulesetDeclaration |
      
      // Parse a rule.
      ParsedRule |
      
      // Parse a ruleset strategy.
      ParsedRulesetStrategy |
      
      // Parse a map strategy.
      ParsedMap |
      
      // Parse a right-map strategy.
      ParsedRMap |
      
      // Parse a match.
      ParsedMatch |
      
      // Parse an operator definition.
      ParsedOperatorDefinition |
        
      // Parse a lambda.
      ParsedLambda |
      
      // Parse a set of bindings.
      ParsedBindings |

      // Parse a typical operator application.
      ParsedApply |
      
      // Parse the special root types.
      /*
      "STRING " ~ push(SimpleTypeNode(STRING)) |
      "SYMBOL " ~ push(SimpleTypeNode(SYMBOL)) |
      "INTEGER " ~ push(SimpleTypeNode(INTEGER)) |
      "FLOAT " ~ push(SimpleTypeNode(FLOAT)) |
      "BOOLEAN " ~ push(SimpleTypeNode(BOOLEAN)) |
      "OPTYPE " ~ push(SimpleTypeNode(OPTYPE)) |
      "RULETYPE " ~ push(SimpleTypeNode(RULETYPE)) |
      "ANY " ~ push(SimpleTypeNode(ANY)) |
      */
      
      // Parse a typed list.
      ParsedTypedList |
      
      // Parse a property set.
      ParsedAlgProp |
      
      // Parse a variable.  The leading dollar sign is used to distinguish
      // between a symbol and a variable.  If a type is not specified for a
      // variable, it gets put in the type universe.
      ParsedVariable |

      // A "naked" operator is specified by explicitly giving the operator
      // type OPTYPE.  Otherwise it is parsed as a symbol.
      ESymbol ~ ": " ~ "OPTYPE " ~~> (
          (sym: NakedSymbolNode) =>
            OperatorNode(sym.str, context.operatorLibrary)) |
       
      // Parse a literal.  A literal can take many forms, but it should be
      // possible to always detect the kind of literal during parse.  By
      // default literals go into a simple type, but this can be overridden.
      ParsedLiteral |
      
      AnyNumber ~ ": " ~ FirstAtom ~~> (
          (num:NumberNode, typ:AstNode) => num.retype(typ)) |
      AnyNumber |

      // Parse the special type universe.
      "^TYPE " ~> (x => TypeUniverseNode()))
  }.label("a simple atom")
  
  //======================================================================
  // Parse a simple operator application.  The other form of application
  // (the more general kind) is parsed in Atom.
  //======================================================================

  /**
   * Parse the "usual" operator application form.
   */
  def ParsedApply = rule {
    // Parse an operator application.  This just applies an operator to
    // some other atom.  The operator name is given as a symbol, and the
    // argument may be enclosed in parens if it is a list, or joined to
    // the operator with a dot if not.  The dot form is parsed elsewhere.
    //
    // If you want to use a general atom, use a dot to join it to the argument.
    // The same comment applies if you want to use a general atom as the
    // argument.
    ESymbol ~ "( " ~ ParsedAtomSeq ~ ") " ~~> (
      (op: NakedSymbolNode, arg: AtomSeqNode) =>
        ApplicationNode(context, op, arg))
  }.label("an operator application")
  
  //======================================================================
  // Parse a lambda.
  //======================================================================

  /**
   * Parse a lambda expression.
   */
  def ParsedLambda = rule {
    "\\ " ~ ParsedVariable ~ ". " ~ FirstAtom ~~> (
        (lvar: VariableNode, body: AstNode) => LambdaNode(lvar, body))
  }.label("a lambda expression")
  
  //======================================================================
  // Parse trivial literals.
  //======================================================================

  /**
   * Parse a literal symbol or a literal string.
   */
  def ParsedLiteral = rule {
      ESymbol ~ ": " ~ FirstAtom ~~>
      	((sym: NakedSymbolNode, typ: AstNode) =>
      	  SymbolLiteralNode(Some(typ), sym.str)) |
      ESymbol ~~>
      	((sym: NakedSymbolNode) =>
      	  SymbolLiteralNode(None, sym.str)) |
      EString ~ ": " ~ FirstAtom ~~>
      	((str: String, typ: AstNode) => StringLiteralNode(typ, str)) |
      EString ~~>
      	((str: String) => StringLiteralNode(SimpleTypeNode(STRING), str))
  }.label("a literal expression")

  /** Parse a double-quoted string. */
  def EString = rule {
    val str = new StringBuilder()
    "\"" ~ zeroOrMore(Character(str)) ~~> (x => construct(x)) ~ "\" "
  }.label("a string")

  /**
   * Parse a character in a string.  The character is added to the end of the
   * string passed in (if any) and the composite string is returned.  Escapes
   * are interpreted here.
   * 
   * @param str		The string to get the new character.  May be unspecified.
   * @return	The new string.
   */
  def Character(str: StringBuilder) = rule {
    (EscapedCharacter | NormalCharacter) ~> (x => x)
  }.label("a single character")

  /** Parse an escaped character. */
  def EscapedCharacter = rule {
    "\\" ~ anyOf("""`"nrt\""")
  }.label("a character escape sequence")

  /** Parse a normal character. */
  def NormalCharacter = rule { noneOf(""""\""") }.label("a character")

  /** Parse a symbol. */
  def ESymbol = rule {
    val str = new StringBuilder()
    "`" ~ zeroOrMore(SymChar) ~~> (x => NakedSymbolNode(construct(x))) ~ "` " |
    group(("a" - "z" | "A" - "Z" | "_") ~ zeroOrMore(
      "a" - "z" | "A" - "Z" | "0" - "9" | "_")) ~> (NakedSymbolNode(_)) ~ WS
  }.label("a symbol")

  /** Parse a character that is part of a symbol. */
  def SymChar = rule {
    (EscapedCharacter | SymNorm) ~> (x => x)
  }.label("a single character")
  
  /** Parse a "normal" non-escaped character that is part of a symbol. */
  def SymNorm = rule { noneOf("""`\""") }.label("a character")

  //======================================================================
  // Parse property lists.
  //======================================================================
  
  def ParsedAlgProp = rule {
    ParsedLongPropertyList | ParsedShortPropertyList
  }
  
  def ParsedLongPropertyList = rule {
    "{ " ~ "prop " ~ zeroOrMore(
        ("associative "|"A ") ~ "= " ~ FirstAtom ~~> ((x) => AssociativeNode(x)) |
        ("commutative "|"C ") ~ "= " ~ FirstAtom ~~> ((x) => CommutativeNode(x)) |
        ("idempotent "|"I ") ~ "= " ~ FirstAtom ~~> ((x) => IdempotentNode(x)) |
        ("absorber "|"B ") ~ "= " ~ FirstAtom ~~> ((x) => AbsorberNode(x)) |
        ("identity "|"D ") ~ "= " ~ FirstAtom ~~> ((x) => IdentityNode(x)),
        ", "
        ) ~ "} " ~~>
    (AlgPropNode(_))
  }
  
  def ParsedShortPropertyList = rule {
    "%" ~ zeroOrMore(
        ignoreCase("B") ~ "[ " ~ Atom ~ "]" ~~> ((x) => AbsorberNode(x)) |
        ignoreCase("D") ~ "[ " ~ Atom ~ "]" ~~> ((x) => IdentityNode(x)) |
        ignoreCase("A") ~> ((x) => AssociativeNode(TrueNode)) |
        ignoreCase("C") ~> ((x) => CommutativeNode(TrueNode)) |
        ignoreCase("I") ~> ((x) => IdempotentNode(TrueNode)) |
        ignoreCase("!A") ~> ((x) => AssociativeNode(FalseNode)) |
        ignoreCase("!C") ~> ((x) => CommutativeNode(FalseNode)) |
        ignoreCase("!I") ~> ((x) => IdempotentNode(FalseNode))) ~~>
    (AlgPropNode(_))
  }

  //======================================================================
  // Parse lists of atoms.
  //======================================================================

  /**
   * Parse a "typed" list.  That is, a list whose properties are specified.
   */
  def ParsedTypedList = rule {
    ParsedAlgProp ~ WS ~ "( " ~ ParsedAtomSeq ~ ") " ~~>
    ((props: AlgPropNode, list: AtomSeqNode) => AtomSeqNode(props, list.list))
  }.label("a typed list of atoms")
  
  /**
   * Parse a list of atoms, separated by commas.  No concept of associativity,
   * commutativity, etc., is inferred at this point.
   */
  def ParsedAtomSeq = rule {
    zeroOrMore(Atom, ", ") ~~> (AtomSeqNode(AlgPropNode(), _))
  }.label("a comma-separated list of atoms")

  //======================================================================
  // Parse a map pair.
  //======================================================================
  
  /**
   * Parse a map pair.
   */
  def ParsedMapPair = rule {
    FirstAtom ~ "-> " ~ Atom ~~> (MapPairNode(_,_))
  }
  
  //======================================================================
  // Parse a rewrite rule.
  //======================================================================
  
  /**
   * Parse a ruleset declaration.
   */
  def ParsedRulesetDeclaration = rule {
    "{ " ~ ParsedRulesetList ~ "} " ~~>
    (RulesetDeclarationNode(context, _))
  }.label("a ruleset declaration")

  /**
   * Parse a rule.
   */
  def ParsedRule = rule(
    "{ " ~
    
    // First there are optional variable declarations.  This may go away!
    optional("@ " ~ ParsedVariable ~
        zeroOrMore(anyOf(",@") ~ WS ~ ParsedVariable) ~~> (
        (head: VariableNode, tail: List[VariableNode]) => head :: tail)) ~

    // Next is the rule itself, consisting of a pattern, a rewrite, and zero
    // or more guards.
    ignoreCase("rule") ~ WS ~
    ParsedMapPair ~ zeroOrMore("if " ~ Atom) ~

    // Next the rule can be declared to be in zero or more rulesets.
    optional(ParsedRulesetList) ~

    // Finally the rule's cache level can be declared.  This must be the
    // last item, if present.
    optional("level " ~ AnyInteger) ~ "} " ~~> (
        RuleNode(
            _: Option[List[VariableNode]],
            _: MapPairNode,
            _: List[AstNode],
            _: Option[List[NakedSymbolNode]],
            _: Option[UnsignedIntegerNode]))).label("a rewrite rule")
            
  /**
   * Parse a comma-separated list of ruleset names, introduced by the keyword
   * `ruleset` or `rulesets`.
   */
  def ParsedRulesetList = rule {
    (ignoreCase("rulesets ") | ignoreCase("ruleset ")) ~
    zeroOrMore(ESymbol, ", ")
  }.label("a comma-separated ruleset list")
  
  /**
   * Parse a ruleset strategy.
   */
  def ParsedRulesetStrategy = rule {
    "{ " ~ "apply " ~ ParsedRulesetList ~ "} " ~~>
    (RulesetsNode(context, _))
  }.label("a ruleset application")
  
  /**
   * Parse a map strategy.
   */
  def ParsedMap = rule {
    "{ " ~ "map " ~
    zeroOrMore("@ " ~ ESymbol) ~
    zeroOrMore("- " ~ "@ " ~ ESymbol) ~
    Atom ~ "} " ~~>
    (MapNode(_,_,_))
  }.label("a map expression")
  
  /**
   * Parse a rmap strategy.
   */
  def ParsedRMap = rule {
    "{ " ~ "rmap " ~ Atom ~ "} " ~~>
    (RMapNode(_))
  }.label("a right-map expression")

  //======================================================================
  // Parse variables.
  //======================================================================
  
  /**
   * Parse a variable.
   */
  def ParsedVariable = rule {
    ParsedTermVariable | ParsedMetaVariable
  }.label("a variable")

  def ParsedTermVariable = rule {
    "$" ~ (ParsedTypedVariable | ParsedUntypedVariable)
  }.label("a term variable")
  
  def ParsedMetaVariable = rule {
    "$$" ~ (ParsedTypedVariable | ParsedUntypedVariable) ~~> (
        vx => MetaVariableNode(vx))
  }.label("a metavariable")
  
  def ParsedTypedVariable = rule {
    ESymbol ~ optional("{ " ~ Atom ~ "} ") ~ ": " ~ FirstAtom ~
    zeroOrMore("@" ~ ESymbol) ~~> (
      (sval: NakedSymbolNode, grd: Option[AstNode], typ: AstNode,
          list: List[NakedSymbolNode]) =>
        VariableNode(typ, sval.str, grd, list.map(_.str).toSet))
  }.label("a variable with type information")
  
  def ParsedUntypedVariable = rule {
    ESymbol ~ optional("{ " ~ Atom ~ "} ") ~
    zeroOrMore("@" ~ ESymbol) ~~> (
      (sval, grd, list) =>
        VariableNode(SimpleTypeNode(EANY), sval.str, grd,
            list.map(_.str).toSet))
  }.label("an untyped variable")

  //======================================================================
  // Parse whitespace.
  //======================================================================

  /** Parse ignorable whitespace. */
  def WS: Rule0 = rule { SuppressNode
    zeroOrMore(
        // Whitesapce.
        oneOrMore(anyOf(" \n\r\t\f")) |
        "/*" ~ zeroOrMore(&(!"*/") ~ PANY) ~ "*/" |
        "//" ~ zeroOrMore(&(!anyOf("\r\n")) ~ PANY) ~ ("\r\n" | "\r" | "\n" | EOI)
        )
  }.label("whitespace or comments")

  //======================================================================
  // Parse a number.
  //======================================================================

  /**
   * Parse a number.  The number can be an integer or a float, and can be
   * positive or negative.
   */
  def AnyNumber: Rule1[NumberNode] = rule {
    optional("-" ~ push(false)) ~ (
      HNumber |
      BNumber |
      DNumber |
      ONumber) ~ WS ~~> (NumberNode(_:Option[Boolean],_:UnsignedIntegerNode,
          _:Option[UnsignedIntegerNode],_:Option[SignedIntegerNode]))
  }.label("an integer or floating point number")

  /**
   * Parse a hexadecimal number that may be either an integer or a float.
   */
  def HNumber = rule {
    HInteger ~
      optional("." ~ zeroOrMore(HDigit) ~> (UnsignedIntegerNode(_, 16))) ~
      optional(ignoreCase("p") ~ Exponent)
  }.label("a hexadecimal number")

  /**
   * Parse a binary number that may be either an integer or a float.
   */
  def BNumber = rule {
    BInteger ~
      optional("." ~ zeroOrMore(BDigit) ~> (UnsignedIntegerNode(_, 2))) ~
      optional((ignoreCase("e") | ignoreCase("p")) ~ Exponent)
  }.label("a binary number")

  /**
   * Parse a decimal number that may be either an integer or a float.
   */
  def DNumber = rule {
    DInteger ~
      optional("." ~ zeroOrMore(DDigit) ~> (UnsignedIntegerNode(_, 10))) ~
      optional((ignoreCase("e") | ignoreCase("p")) ~ Exponent)
  }.label("a decimal number")

  /**
   * Parse an octal number that may be either an integer or a float.
   */
  def ONumber = rule {
    OInteger ~
      optional("." ~ zeroOrMore(ODigit) ~> (UnsignedIntegerNode(_, 8))) ~
      optional((ignoreCase("e") | ignoreCase("p")) ~ Exponent)
  }.label("an octal number")

  /**
   * Parse an exponent expression.  The expression does not include the
   * linking "e" or "p" exponent indicator.
   */
  def Exponent = rule {
      optional("+" ~ push(true) | "-" ~ push(false)) ~
      AnyInteger ~~> (SignedIntegerNode(_, _))
  }.label("an exponent")

  /**
   * Parse an integer in hexadecimal, decimal, octal, or binary.
   * @return	An unsigned integer.
   */
  def AnyInteger = rule { (
    HInteger |
    BInteger |
    DInteger |
    OInteger ) ~ WS
  }.label("an integer")

  /**
   * Parse a hexadecimal integer.
   * @return	An unsigned integer.
   */
  def HInteger = rule {
    ignoreCase("0x") ~ oneOrMore(HDigit) ~> (UnsignedIntegerNode(_: String, 16))
  }.label("a hexadecimal integer")

  /**
   * Parse a binary integer.
   * @return	An unsigned integer.
   */
  def BInteger = rule {
    ignoreCase("0b") ~ oneOrMore(BDigit) ~> (UnsignedIntegerNode(_: String, 2))
  }.label("a binary integer")

  /**
   * Parse a decimal integer.
   * @return	An unsigned integer.
   */
  def DInteger = rule {
    group(("1" - "9") ~ zeroOrMore(DDigit)) ~>
    (UnsignedIntegerNode(_: String, 10))
  }.label("a decimal integer")

  /**
   * Parse an octal integer.
   * @return	An unsigned integer.
   */
  def OInteger = rule {
    group("0" ~ zeroOrMore(ODigit)) ~> (UnsignedIntegerNode(_: String, 8))
  }.label("an octal integer")

  /** Parse a decimal digit. */
  def DDigit = rule { "0" - "9" }.label("a decimal digit")
  
  /** Parse an octal digit. */
  def ODigit = rule { "0" - "7" }.label("an octal digit")
  
  /** Parse a hexadecimal digit. */
  def HDigit = rule {
    "0" - "9" | "a" - "f" | "A" - "F"
  }.label("a hexadecimal digit")
  
  /** Parse a binary digit. */
  def BDigit = rule { "0" | "1" }.label("a binary digit")
  
  //======================================================================
  // Define an operator.
  //======================================================================
  
  /**
   * Parse an operator definition.
   * 
   * Operator definitions look as follows.
   * 
   * An operator whose definition is provided by a body.  Whenever the operator
   * is encountered, it is replaced at construction time by binding the formal
   * parameters and then rewriting the body.  It is essentially a macro.
   * {{{
   *   { operator abel(\$x: STRING, \$y: ^TYPE): ^TYPE = cain(\$x, seth(\$y)) }
   * }}}
   * A symbolic operator whose properties are specified, if any.
   * {{{
   *   { operator join(\$x: ^TYPE, \$y: ^TYPE): ^TYPE }
   *   { operator product(\$x: NUMBER, \$y: NUMBER): NUMBER is
   *     associative, commutative, absorber 0, identity 1 }
   *   { operator or(\$p: BOOLEAN, \$q: BOOLEAN): BOOLEAN is
   *     associative, commutative, idempotent, identity false, absorber true }
   * }}} 
   */
  def ParsedOperatorDefinition = rule {
    ParsedSymbolicOperatorDefinition |
    ParsedImmediateOperatorDefinition
  }.label("an operator definition")
  
  /**
   * Parse a symbolic operator definition.
   * {{{
   * { operator join(\$x: ^TYPE, \$y: ^TYPE): ^TYPE }
   * { operator product(\$x: NUMBER, \$y: NUMBER): NUMBER is
   *   associative, commutative, absorber 0, identity 1 }
   * { operator or(\$p: BOOLEAN, \$q: BOOLEAN): BOOLEAN is
   *   associative, commutative, idempotent, identity false, absorber true }
   * }}}
   */
  def ParsedSymbolicOperatorDefinition: Rule1[OperatorDefinitionNode] = rule {
    "{ " ~ "operator " ~ ParsedOperatorPrototype ~ ParsedOperatorProperties ~
    "} " ~~> (SymbolicOperatorDefinitionNode(_,_))
  }.label("a symbolic operator definition")
  
  /**
   * Parse an immediate operator definition.
   * {{{
   * { operator abel(\$x: STRING, \$y: ^TYPE): ^TYPE = cain(\$x, seth(\$y)) }
   * { macro body(\\$x.\$body:\$T):\$T = \$body }
   * }}}
   */
  def ParsedImmediateOperatorDefinition: Rule1[OperatorDefinitionNode] = rule {
    "{ " ~ "operator " ~ ParsedOperatorPrototype ~ ParsedImmediateDefinition ~
    "} " ~~> (ImmediateOperatorDefinitionNode(_,_))
  }.label("an immediate operator definition")
  
  /** Parse an operator prototype. */
  def ParsedOperatorPrototype = rule {
    ESymbol ~ "( " ~ ParsedParameterList ~ ") " ~
    optional(": " ~ FirstAtom) ~
    zeroOrMore("if " ~ FirstAtom) ~~>
    ((name:NakedSymbolNode, pars:List[VariableNode],
        typ:Option[AstNode], guards:List[AstNode]) =>
          new OperatorPrototypeNode(name.str, pars, typ, guards))
  }.label("an operator prototype")

  /** Parse a parameter list. */
  def ParsedParameterList = rule {
    zeroOrMore(ParsedTermVariable, ", ")
  }.label("an operator parameter list")
  
  /** Parse an immediate definition for an operator. */
  def ParsedImmediateDefinition = rule {
    "= " ~ Atom
  }.label("equal (=) and the immediate operator's body")
  
  /** Parse a sequence of operator properties. */
  def ParsedOperatorProperties = rule {
    optional(
        ParsedAlgProp |
        "is " ~ oneOrMore(ParsedOperatorProperty, ", "
    ) ~~> (AlgPropNode(_))) ~~> {
      prop => prop match {
        case None => AlgPropNode()
        case Some(apn) => apn
      }
    }
  }.label("the operator properties")
  
  /**
   * Parse an operator property.
   * @param pop		An operator properties node to fill in.
   */
  def ParsedOperatorProperty: Rule1[PropertyNode] = rule {
      "associative " ~> (x => AssociativeNode(TrueNode)) |
      "commutative " ~> (x => CommutativeNode(TrueNode)) |
      "idempotent " ~> (x => IdempotentNode(TrueNode)) |
      "absorber " ~ Atom ~~> (AbsorberNode(_)) |
      "identity " ~ Atom ~~> (IdentityNode(_))
  	}.label("an operator property")
  
  //======================================================================
  // Bindings.
  //======================================================================
  
  /**
   * Parse a set of bindings.
   */
  def ParsedBindings = rule {
    "{ " ~ "bind " ~ zeroOrMore(ParsedBind, ", ") ~ "} " ~~> (BindingsNode(_))
  }.label("a binding expression")

  /**
   * Parse a single bind.
   */
  def ParsedBind = rule {
    ESymbol ~ "-> " ~ Atom ~~> (_ -> _)
  }.label("a single binding")
  
  //======================================================================
  // Matching.
  //======================================================================
  
  /**
   * Parse a match between two atoms.
   */
  def ParsedMatch = rule {
    "{ " ~ "match " ~ Atom ~ "} " ~~>
    (pat => MatchNode(pat))
  }.label("a match expression")

  //======================================================================
  // Other methods affecting the parse.
  //======================================================================
  
  /**
   * Eliminate trailing whitespace.  This trick is found on the Parboiled web
   * site in the examples.
   * @param string	Parsed text.
   */
  override implicit def toRule(string: String) =
    if (string.endsWith(" ")) str(string.trim) ~ WS else str(string)
}
