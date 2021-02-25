.PHONY: all clean package test
all: ducks

package: clean
	sbt graalvm-native-image:packageBin

ducks: test
	mv target/graalvm-native-image/ducks $@

clean:
	rm -f ducks test-output/*
	rmdir test-output
	sbt clean

test: package
	target/graalvm-native-image/ducks -c test-data/Conditions.csv -1 test-data/test1.dmux.fastq.gz -2 test-data/test1.data.fastq.gz -o test-output
	for F in $$(ls test-output); do diff test-output/$$F test-data/expected-output/$$F; done
