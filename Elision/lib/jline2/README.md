Description
-----------

JLine is a Java library for handling console input. It is similar in functionality to [BSD editline](http://www.thrysoee.dk/editline/) and [GNU readline](http://www.gnu.org/s/readline/). People familiar with the readline/editline capabilities for modern shells (such as bash and tcsh) will find most of the command editing features of JLine to be familiar.

JLine 2.x is an evolution of [JLine](https://github.com/jline/jline) which was previously maintained at [SourceForge](http://jline.sourceforge.net/).

License
-------

JLine is distributed under the [BSD](http://www.opensource.org/licenses/bsd-license.php) license, meaning that you are completely free to redistribute, modify, or sell it with almost no restrictions. For more information on the BSD license, see [http://www.opensource.org/licenses/bsd-license.php].

Forums
------

* [jline-users](https://groups.google.com/group/jline-users)
* [jline-dev](https://groups.google.com/group/jline-dev)

Maven Usage
-----------

Use the following definition to use JLine in your maven project:

    <dependency>
      <groupId>jline</groupId>
      <artifactId>jline</artifactId>
      <version>2.6</version>
    </dependency>

Building
--------

### Requirements

* Maven 2+
* Java 5+

Check out and build:

    git clone git://github.com/jline/jline2.git
    cd jline2
    mvn install


To build the jdk14 jar:

    mvn install -Dretro
