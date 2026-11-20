APPDIR ?= /Applications
BINDIR ?= $(HOME)/.local/bin

APP := kDiffViewer.app
BUILT_APP := app/build/compose/binaries/main/app/$(APP)

# The installed bundle is removed first: ditto merges into an existing directory, so files a newer build no longer ships would survive.
.PHONY: install
install:
	@test "$$(uname)" = Darwin || { echo "make install supports macOS only; on Linux install the .deb built by ./gradlew :app:packageDeb" >&2; exit 1; }
	./gradlew :app:createDistributable
	rm -rf "$(APPDIR)/$(APP)"
	ditto "$(BUILT_APP)" "$(APPDIR)/$(APP)"
	mkdir -p "$(BINDIR)"
	ln -sfn "$(APPDIR)/$(APP)/Contents/app/resources/kdv" "$(BINDIR)/kdv"
