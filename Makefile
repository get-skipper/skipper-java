.PHONY: build test sync publish clean

build:
	./gradlew build -x test

test:
	./gradlew test

sync:
	SKIPPER_MODE=sync ./gradlew test

publish:
	./gradlew publish

clean:
	./gradlew clean
