# JarPlant ALL THE THINGS!

A proof-of-concept of a self-replicating malware for Maven build environments.

> [!CAUTION]
> This is a potentially malicious software used for security testing.
> Running this software may seriously mess up your development environment.
> Tread carefully.

It uses [JarPlant](https://github.com/w1th4d/JarPlant)  to inject itself into all [JAR (Java Archive)](https://en.wikipedia.org/wiki/JAR_(file_format)) 
files found under the local [Maven](https://maven.apache.org/) repository directory (`~/.m2/repository`).
Any subsequent build that depends on an affected artifact will do the same, carrying the infection forward.

## But, why?

This project was conceived as a demonstration of how a build-time malware could spread and infest a build server through
means of _Cross Build Injection_.
The code was made public to emphasize the need for detection and prevention against such attacks.

This project demonstrates how a rogue Java project can infect (or _spike_) all artifacts cached locally on a build 
server that lacks proper isolation between builds (which is the common default most [Jenkins](https://www.jenkins.io/) 
servers).
The payload itself self-replicates (like a classic _computer virus_).
The next build that depends upon any infected artifact also runs the malicious code and re-infect the system. 
Further, if a build pipeline publishes infected artifacts to a package registry, the potential spread can increase 
significantly, affecting developer workstations and eventually production servers downstream.

The end result can be a persistent backdoor in all JVM applications (and libraries) that the infested build environment
creates. The backdoor leaves no traces in the source code and is typically injected before any artifact signing, 
making it very difficult to detect.
The current source code does not carry any actual backdoor, but rather serves as a proof-of-concept.

As we've warned about this technique on various security conferences (like [Sec-T 0x10](https://www.youtube.com/watch?v=Wcz-Gvm-468) 
and [Disobey 25](https://www.youtube.com/watch?v=U5yFcbRRQ78)), we hope to shed light and attention on the problem.
The purpose of this project is to prove that it is possible and ultimately to push for improvements in software 
build-time and supply-chain security.

## Preparations

Change the `CONF_DOMAIN` to your out-of-bounds DNS catcher (like [Interactsh](https://app.interactsh.com/#/) or 
[Burp Collaborator](https://portswigger.net/burp/documentation/collaborator)).
Leaving this one unset or empty will have the implant skip DNS exfil queries.

You may want to change the `CONF_TARGET_HOSTNAME` value to be aligned with your lab environment.

Build this project:
```
mvn clean package
```

## Initial infection

Depending on your scenario, you may either just _run it manually_ or _emulate a supply-chain attack_.

### Run it manually

To manually run it on your build server, first copy `target/JarPlant-all-the-things-0.2-SNAPSHOT-jar-with-dependencies.jar`
over to the server and then run it in a terminal:
```shell
java -jar target/JarPlant-all-the-things-0.2-SNAPSHOT-jar-with-dependencies.jar --all
```

Watch it explode.

### Emulating supply-chain attacks

To fully simulate a supply-chain attack scenario, you may instead want to build (not manually run) this project on 
the build server.
Modify [DetonateDuringTests](src/test/java/org/example/implants/DetonateDuringTests.java) to not `@Ignore` the test in 
that file. Doing so will trigger the payload during the test phase of the Maven build process.
Be careful.
Finally, set up this project on your build server just as you would with any other code repository.

Alternatively, you may go fancy with build-time components and create something similar to Jeremy Long's 
[malicious-dependencies](https://github.com/jeremylong/malicious-dependencies) project.
Be ready to jump through some hoops to get it right.

Another viable option is to simulate a [dependency hijacking attack](https://owasp.org/www-project-top-10-ci-cd-security-risks/CICD-SEC-03-Dependency-Chain-Abuse) 
by manually spiking a specific JAR file that is used by a build process:

```shell
java -jar target/JarPlant-all-the-things-0.2-SNAPSHOT-jar-with-dependencies.jar /path/to/target.jar
```

Then have the spiked JAR be served to your target build environment.
How you do this depends on your scenario, but it could be through a compromized local artifact repository,
by MITM, a custom rogue repository mirror, or whatever you fancy.

Please don't break the internet.

