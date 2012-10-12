/*       _ _     _
 *   ___| (_)___(_) ___  _ __
 *  / _ \ | / __| |/ _ \| '_ \
 * |  __/ | \__ \ | (_) | | | |
 *  \___|_|_|___/_|\___/|_| |_|
 *
 * Copyright (c) 2012 by Stacy Prowell (sprowell@gmail.com).
 * All rights reserved.  http://stacyprowell.com
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 *  - Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package ornl.elision
import scala.collection.mutable.StringBuilder
import ornl.elision.util.Text

/**
 * This is the entry point when running from the jar file.  This also provides
 * the `fail` method that prints out command line invocation errors.
 */
object Main extends App {
  
  /**
   * Print out an error message about the command line, highlighting the
   * offending element.  You should follow this with calling `sys.exit(N)`,
   * where `N` is the (non-zero) exit value you want.
   * 
   * @param args      The command line arguments.
   * @param position  The index of the bad argument or switch.
   * @param err       Text describing the error.
   */
  def fail(args: Array[String], position: Int, err: String) {
    (new Text()).addln("ERROR: "+err).wrap(80) foreach (println(_))
    println()
    var index = 0
    var line = ""
    while (index < position) {
      print(args(index))
      print(" ")
      line += " "*(args(index).length + 1)
      index += 1
    } // Move to the offending item.
    println(args(index))
    print(line)
    println("^"*(args(index).length))
  }
  
  /**
   * Print usage information.
   */
  private def _usage(): Option[String] = {
    println("Usage:")
    println("[global switches...] [command] [command switches and arguments...])")
    println()
    println("Global switches:")
    SwitchUsage(globals)
    println()
    println("Try -h after a command to see help on the command.")
    None
  }
  
  // Define the special global switches that can come before the command.
  val globals = Seq(
      Switch(Some("help"), Some('h'), "Provide basic usage information.", _usage _),
      ArgSwitch(Some("debug"), Some('d'), "Enable a debugging tag.", "TAG",
          (tag: String) => {
            Debugger.enableDebugModes(tag, Debugger.Mode.ON)
            None
          })
  )

  // Process the arguments and invoke the correct item.  If we received no
  // command, we pass the empty string to get the default command.
  try {
    // Process the switches.  Stop when the first argument is encountered.
    // To do this, we implement an argument handler that sets a flag and then
    // generates an error that terminates the argument parse.  We preserve
    // the argument.
    var okay = false
    var cmd = ""
    def firstarg(arg: String) = {
      okay = true
      cmd = arg
      (Some(""), true)
    }
    val (remain, err, pos) = Switches(args, globals, firstarg _)
    
    // Check for an actual error, and display it if we find one.  We then exit.
    if (err != None && !okay) {
      // There was an actual error!
      fail(args, pos, err.get)
      sys.exit(1)
    }
    
    // Decide what to do.  If we have a command, invoke it.  If not, then
    // invoke the default command.
    if (!okay) {
      // Perform the default action.
      Version.invoke("", args.slice(pos+1, args.length))
    } else {
      // Remove the command, and invoke with the remainder.
      Version.invoke(cmd, args.slice(pos+1, args.length))
    }
  } catch {
    case ex: Version.MainException =>
      println("ERROR: " + ex.getMessage)
<<<<<<< HEAD
  }
  
  /**
   * Provide command line help.
   */
  def help = {
    val buf = new StringBuilder
    buf.append(
        """|Usage: [global switches] [command] [command switches]
           |Execute the command [command].  Prior to this, the switches given
           |by [global switches] are processed.  The [command switches] are
           |passed to the selected [command].
           |
           |Global Switches:
           |""".stripMargin)
    buf.append(
        """|Command Switches:
           |""".stripMargin)
  }
  
  /**
   * Process the command line arguments, extract any switches and process them,
   * and then return the resulting structure.
   * 
   * @param args  The command line arguments.
   * @return  The processed arguments.
   */
  def processArguments(args: Array[String]) = {
    var index = 0
    while (index < args.size) {
      // See if this argument starts with a dash.  If so, it is possibly a
      // switch, and we will process it as such.
      val arg = args(index)
      if (arg.startsWith("-")) {
        // The argument starts with a dash; it might be a switch.  Try to match
        // it against the different permissible kinds of switches.  We start
        // with the most complex first.
        val Assignment = "^--([^=]+)=(.*)$".r
        val Longswitch = "^--(.*)$".r
        val Shortswitch = "^-(.*)$".r
        arg match {
          case Assignment(name, value) =>
            // This is a switch that assigns a value to some named option.
          case Longswitch(name) =>
            // This is just a long switch.
          case Shortswitch(singles) =>
            // This can be a collection of short switches (and maybe even
            // their arguments) on a single dash.
          case _ => // Does not match; skip it.
        }
      }
      
      // Move to next argument.
      index += 1
    } // Process all command line arguments.
=======
      sys.exit(2)
>>>>>>> 1e8a60c... Significant changes to the command line interface to allow using
  }
}
