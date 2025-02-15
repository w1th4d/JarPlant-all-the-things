# JarPlant ALL THE THINGS!

A PoC of a self-replicating [JarPlant](https://github.com/w1th4d/JarPlant) implant tailored for Maven.

> **Handle with care. Do not run.**

This thing will inject itself into all JARs it can find under the local Maven repository directory (`~/.m2/repository`).
It does not have any malicious end-game payload, but it may seriously mess up any Maven environment.


## Quickstart

Change the `CONF_DOMAIN` to your out-of-bounds DNS catcher (like Interactsh or Burp Collaborator).

You may want to change the `CONF_TARGET_HOSTNAME` value to be aligned with your lab environment.

Build this thing:
```
mvn clean package
```

Run it:
```
java -jar target/JarPlant-all-the-things-1.0-SNAPSHOT-jar-with-dependencies.jar
```

Watch it explode.


## Future work

There's a lot of potential to make this thing a lot faster through parallelization, smarter choices and optimizations in the spiking itself.

It logs a lot (on purpose) and this could be avoided to make it more stealthy.

Including a separate end-game payload can be a bit fiddly. This could be improved.

It uses a rudimentary hostname check to determine the environment. There may be more elaborate ways of doing this.
