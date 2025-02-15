# JarPlant ALL THE THINGS!

Self-replicating implant. Handle with care.

## Quickstart

Since JarPlant is not released in any public repository, install it locally:
```
cd ../JarPlant/
mvn clean install
```

JarPlant should not exist in your local `~/m2/repository` to be used as a dependency.

Build this thing:
```
cd ../JarPlant-all-the-things
mvn clean package
```

For various deeply quirky reasons, this project will most likely fail to do its thing when run from an IDE (like IntelliJ).
Instead, run the actual JAR. This is due to how JarPlant currently searches for the place where it can find implants
(in this case: itself). This may be improved upon in the future.

Anyway, just run it like this (after packaging):
```
java -jar target/JarPlant-all-the-things-1.0-SNAPSHOT-jar-with-dependencies.jar
```

Watch it explode.

## Future work



