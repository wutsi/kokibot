# Skill: MarkItDown

Convert files and office documents to Markdown.

[![](https://github.com/microsoft/markitdown)](https://github.com/jgm/pandoc)
[![](https://img.shields.io/badge/python-green.svg?logo=python)](https://nodejs.org/)

## Setup

In order to use this skill, you need to have `pandoc` installed, and `tectonic` installed for PDF conversions.

### Install Pandoc

``` bash
# Install with all features
pip3 install 'markitdown[all]'

# Or from source
git clone https://github.com/microsoft/markitdown.git
cd markitdown
pip install -e 'packages/markitdown[all]'
```
