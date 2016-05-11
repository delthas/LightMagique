CLIENTPATH=fr/delthas/lightmagique/client/Client
CLIENTCLASS=fr.delthas.lightmagique.client.Client
SERVERPATH=fr/delthas/lightmagique/server/Server
SERVERCLASS=fr.delthas.lightmagique.server.Server

target/client.jar: bin/$(CLIENTPATH).class
	mkdir -p target
	jar cfe %@ $(CLIENTCLASS) -C bin/ .
	jar uf %@ -C res/ .

target/server.jar: bin/$(SERVERPATH).class
	mkdir -p target
	jar cfe %@ $(SERVERCLASS) -C bin/ .
	jar uf %@ -C res/ .

bin/$(CLIENTPATH).class:
	mkdir -p bin
	javac -cp src -d bin -encoding UTF-8 -source 8 -target 8 src/$(CLIENTPATH).java

bin/$(SERVERPATH).class:
	mkdir -p bin
	javac -cp src -d bin -encoding UTF-8 -source 8 -target 8 src/$(SERVERPATH).java

all: target/client.jar target/server.jar
clean:
	mkdir -p bin
	rm -rf bin/
client: target/client.jar
server: target/server.jar
run-client: target/client.jar
	java -jar %<
run-server: target/server.jar
	java -jar %<

.PHONY: all clean client server run-client run-server