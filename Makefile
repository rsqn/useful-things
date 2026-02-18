# Useful-things Maven release Makefile
# Target 'release': build, release to Maven Central, bump to next SNAPSHOT and commit

.PHONY: build ensure-snapshot release help clean compile test package install deploy site dependency-tree bump-patch

help:
	@echo "Targets:"
	@echo "  make clean           - mvn clean"
	@echo "  make compile         - mvn compile"
	@echo "  make test            - mvn test"
	@echo "  make package         - mvn package"
	@echo "  make install         - mvn install"
	@echo "  make deploy          - mvn deploy"
	@echo "  make site            - mvn site"
	@echo "  make dependency-tree - mvn dependency:tree"
	@echo "  make build           - mvn clean verify"
	@echo "  make clean-install   - mvn clean install (single reactor)"
	@echo "  make release         - build, release to Maven Central, bump to next SNAPSHOT (committed)"
	@echo "  make bump-patch      - bump to next patch version and ensure -SNAPSHOT"

# Get current version from POM (requires mvn)
CURRENT_VERSION := $(shell mvn help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null || echo "unknown")

# Ensure version ends with -SNAPSHOT (required by maven-release-plugin)
# Commits the change if poms were modified (release:prepare requires clean working tree)
ensure-snapshot:
	@if ! echo "$(CURRENT_VERSION)" | grep -q SNAPSHOT; then \
		echo "Adding -SNAPSHOT to version $(CURRENT_VERSION)..."; \
		mvn versions:set -DnewVersion=$(CURRENT_VERSION)-SNAPSHOT -DgenerateBackupPoms=false; \
		git add pom.xml */pom.xml && git commit -m "Prepare for release - set version to $(CURRENT_VERSION)-SNAPSHOT"; \
	else \
		echo "Version $(CURRENT_VERSION) is already a SNAPSHOT"; \
	fi

# Bump to next patch version and ensure -SNAPSHOT
bump-patch:
	@echo "Current version: $(CURRENT_VERSION)"
	@BASE_VERSION=$$(echo "$(CURRENT_VERSION)" | sed 's/-SNAPSHOT//'); \
	MAJOR=$$(echo "$$BASE_VERSION" | cut -d. -f1); \
	MINOR=$$(echo "$$BASE_VERSION" | cut -d. -f2); \
	PATCH=$$(echo "$$BASE_VERSION" | cut -d. -f3); \
	NEW_PATCH=$$((PATCH + 1)); \
	NEW_VERSION="$$MAJOR.$$MINOR.$$NEW_PATCH-SNAPSHOT"; \
	echo "Bumping version to $$NEW_VERSION..."; \
	mvn versions:set -DnewVersion=$$NEW_VERSION -DgenerateBackupPoms=false

clean:
	mvn clean

compile:
	mvn compile

test:
	mvn test

package:
	mvn package

install:
	mvn install

deploy:
	mvn deploy

site:
	mvn site

dependency-tree:
	mvn dependency:tree

# Build (clean verify)
build:
	mvn clean verify

# Clean install (single reactor)
clean-install:
	mvn clean install

# Full release: ensure SNAPSHOT, build, then prepare+perform
# - release:prepare: bumps X.Y.Z-SNAPSHOT -> X.Y.Z (commit, tag), then -> X.Y.(Z+1)-SNAPSHOT (commit)
# - release:perform: checks out tag, deploys to Maven Central
release: ensure-snapshot build
	@echo "Starting Maven release..."
	mvn release:prepare release:perform -B
