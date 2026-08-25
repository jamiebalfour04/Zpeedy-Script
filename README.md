# Zpeedy Script

Zpeedy is an indentation-based descriptive language that compiles through
`ZPECompilerBytecodeBuilder` and executes on the ZPE runtime.

```zpeedy
set numbers to [1, 2, 3]

for every number in numbers
  display number

set x to 0
loop while x is less than 3
  display x
  set x to x plus 1

repeat 2 times
  display "Again"

repeat forever
  display "Once"
  stop loop

when x
  is 1 then
    display "One"
  is 2 then
    display "Two"
  otherwise
    display "Something else"

if x is at least 10 then
  display "Ten or more"
alternatively if x is at least 5 then
  display "Five or more"
otherwise
  display "Less than five"

attempt
  display riskyValue
otherwise on error message
  display message
```

Run a source file with:

```text
zpeedy -r program.zps
```

Install the packaged JAR and the `zpeedy` command with:

```text
java -jar zpeedy.jar --install
java -jar zpeedy.jar --install -memory 2048
```

The JAR is installed in Zpeedy's platform application-data directory under
`jamiebalfour/zpeedy`, independently of ZPE's `jamiebalfour/zpe` directory.

Indentation uses spaces. Tabs are rejected. `display` accepts exactly one value.
