# Useful-things Maven release Makefile
# Target 'release': build, release to Maven Central, bump to next SNAPSHOT and commit

.PHONY: build ensure-snapshot release release-prepare release-perform release-push help clean compile test package install deploy site dependency-tree bump-patch

help:
	@echo "Targets:"
	@echo "  make clean            - mvn clean"
	@echo "  make compile          - mvn compile"
	@echo "  make test             - mvn test"
	@echo "  make package          - mvn package"
	@echo "  make install          - mvn install"
	@echo "  make deploy           - mvn deploy"
	@echo "  make site             - mvn site"
	@echo "  make dependency-tree  - mvn dependency:tree"
	@echo "  make build            - mvn clean verify"
	@echo "  make clean-install    - mvn clean install (single reactor)"
	@echo "  make release-prepare  - Step 1: ensure SNAPSHOT, build, prepare (tag X.Y.Z, bump to X.Y.(Z+1)-SNAPSHOT)"
	@echo "  make release-perform  - Step 2: deploy tagged release to Maven Central"
	@echo "  make release-push     - Step 3: push commits and tags to origin"
	@echo "  make release          - Full release: prepare + perform + push (build, tag, deploy, bump snapshot, push)"
	@echo "  make bump-patch       - bump to next patch version and ensure -SNAPSHOT"

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

# Step 1: Prepare release - ensure SNAPSHOT, build, then prepare
# release:prepare does: X.Y.Z-SNAPSHOT -> X.Y.Z (commit, tag), then -> X.Y.(Z+1)-SNAPSHOT (commit)
release-prepare: ensure-snapshot build
	@echo "Step 1: Preparing release (tag + bump to next SNAPSHOT)..."
	mvn release:prepare -B

# Step 2: Perform release - deploy the tagged release to Maven Central
release-perform:
	@echo "Step 2: Deploying tagged release to Maven Central..."
	mvn release:perform -B

# Step 3: Push commits and tags to origin (release plugin may push, but we ensure it)
release-push:
	@echo "Step 3: Pushing commits and tags to origin..."
	git push origin HEAD
	git push origin --tags

# Full release: prepare, perform, push (build, tag, deploy, bump snapshot, commit, push)
release: release-prepare release-perform release-push
	@echo "Release complete. Version bumped to next SNAPSHOT. Changes pushed to origin."
