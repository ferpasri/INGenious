(() => {
  window.__locatorUtils = {

    getRole: function (el) {
      try {
        let role = el.getAttribute('role');
        const tagName = el.tagName.toLowerCase();

        if (!role) {
          switch (tagName) {
            case 'button':
              role = 'button';
              break;
            case 'a':
              role = el.hasAttribute('href') ? 'link' : '';
              break;
            case 'input':
              const type = el.getAttribute('type')?.toLowerCase() || '';
              if (['checkbox', 'radio'].includes(type)) role = type;
              else if (['submit', 'button'].includes(type)) role = 'button';
              else role = 'textbox';
              break;
            case 'select':
              role = 'combobox';
              break;
            case 'textarea':
              role = 'textbox';
              break;
            default:
              role = '';
          }
        }

        if (!role) return ''; // skip unsupported

        let name = '';

        name = el.getAttribute('aria-label') || el.ariaLabel || el.getAttribute('title');

        if (!name && ['input', 'textarea', 'select'].includes(tagName)) {
            name = el.getAttribute('placeholder') || '';
        }

        if (!name && el.innerText) {
            const trimmedText = el.innerText.trim();
            if (trimmedText !== '') {
                name = trimmedText;
            }
        }

        if (!name || name.trim() === '') return '';

        return `${role.toLowerCase()};${name.trim()}`;
      } catch (e) {
        return '';
      }
    },

    getXPath: function (element) {
      try {
        var path = [];
        while (element && element.nodeType === Node.ELEMENT_NODE) {
          var index = 1;
          var sibling = element.previousSibling;
          while (sibling) {
            if (sibling.nodeType === Node.ELEMENT_NODE && sibling.nodeName === element.nodeName) {
              index++;
            }
            sibling = sibling.previousSibling;
          }
          var tagName = element.nodeName.toLowerCase();
          var pathSegment = tagName + '[' + index + ']';
          path.unshift(pathSegment);
          element = element.parentNode;
        }
        return '/' + path.join('/');
      } catch (error) {
        return '';
      }
    },

    getText: function (el) {
      try {
        // Only direct text nodes, not descendant elements
        const text = Array.from(el.childNodes || [])
          .filter(n => n.nodeType === Node.TEXT_NODE)
          .map(n => n.textContent || '')
          .join('')
          .trim();

        return text || '';
      } catch (e) {
        return '';
      }
    },

    escapeAttributeValue: function (value) {
      return String(value).replace(/\\/g, '\\\\').replace(/"/g, '\\"');
    },

    getStableFormControlCSS: function (el) {
      const tagName = (el.tagName || '').toLowerCase();
      const type = (el.getAttribute('type') || '').toLowerCase();
      if (tagName !== 'input' || !['radio', 'checkbox'].includes(type)) return '';

      const attributes = [`[type="${type}"]`];
      const name = el.getAttribute('name');
      const value = el.getAttribute('value');
      if (name) attributes.push(`[name="${this.escapeAttributeValue(name)}"]`);
      if (value) attributes.push(`[value="${this.escapeAttributeValue(value)}"]`);

      const candidate = `input${attributes.join('')}`;
      try {
        if (document.querySelectorAll(candidate).length === 1) return candidate;
      } catch (e) {
        return '';
      }
      return '';
    },

    getCSS: function (el) {
      let generatedCss = '';
      try {
        const stableFormControlCss = this.getStableFormControlCSS(el);
        if (stableFormControlCss) return stableFormControlCss;

        generatedCss = CssSelectorGenerator.getCssSelector(el);
      } catch (e) {
        generatedCss = el.tagName.toLowerCase() + (el.id ? '#' + el.id : '');
      }
      return generatedCss;
    },

    getLabel: function (el) {
      try {
        if (el.labels && el.labels.length > 0) return el.labels[0].innerText;
        if (el.getAttribute('aria-label')) return el.getAttribute('aria-label');
        if (el.getAttribute('aria-labelledby')) {
          const refId = el.getAttribute('aria-labelledby');
          const labelEl = this.findElementDeep(refId);
          if (labelEl) return labelEl.textContent.trim();
        }

        if (el.id) {
          const label = document.querySelector('label[for="' + el.id + '"]');
          if (label) return label.innerText;
        }
        // Fallback: look up to nearest <label>
        let parentLabel = el.closest('label');
        if (parentLabel) return parentLabel.innerText;
      } catch (e) {}
      return '';
    },

    findElementDeep: function (id, root = document) {
      const traverse = (node) => {
        if (!node) return null;
        if (node.id === id) return node;
        if (node.shadowRoot) {
          const res = this.findElementDeep(id, node.shadowRoot);
          if (res) return res;
        }
        for (const child of node.children || []) {
          const res = traverse(child);
          if (res) return res;
        }
        return null;
      };
      return traverse(root);
    },

    getAltText: function (el) {
      return el.getAttribute('alt') ||
        el.getAttribute('aria-label') ||
        el.getAttribute('title') ||
        el.textContent?.trim() || '';
    },

    getTestId: function (el) {
      return el.getAttribute('data-testid') ||
        el.getAttribute('data-test-id') ||
        el.getAttribute('test-id') ||
        '';
    },

    isElementVisible: function (el) {
      try {
        if (!el || el.nodeType !== Node.ELEMENT_NODE) return false;
        if (el.hasAttribute('hidden')) return false;

        const style = window.getComputedStyle(el);
        if (!style) return false;
        if (style.display === 'none') return false;
        if (style.visibility === 'hidden' || style.visibility === 'collapse') return false;
        if (parseFloat(style.opacity || '1') === 0) return false;

        const rect = el.getBoundingClientRect();
        const hasArea = rect.width > 0 && rect.height > 0;
        return hasArea;
      } catch (e) {
        return true;
      }
    },

    isModalElement: function (el) {
      if (!el || el.nodeType !== Node.ELEMENT_NODE) return false;

      const tag = (el.tagName || '').toLowerCase();
      const role = (el.getAttribute('role') || '').toLowerCase();
      const ariaModal = (el.getAttribute('aria-modal') || '').toLowerCase();
      const id = (el.id || '').toLowerCase();
      const className = (el.className || '').toString().toLowerCase();
      const ariaHidden = (el.getAttribute('aria-hidden') || '').toLowerCase();

      let looksLikeModal = false;

      // Explicit dialogs
      if (tag === 'dialog') looksLikeModal = true;
      if (ariaModal === 'true') looksLikeModal = true;
      if (role === 'dialog' || role === 'alertdialog') looksLikeModal = true;

      const namePattern = id + ' ' + className;
      // Common naming patterns for modals / popups
      if (namePattern.includes('modal') ||
          namePattern.includes('popup') ||
          namePattern.includes('dialog')) {
        looksLikeModal = true;
      }
      // Cookie banners / consent overlays
      if (namePattern.includes('cookie') || 
          namePattern.includes('consent')) {
        looksLikeModal = true;
      }

      if (!looksLikeModal) return false;
      if (ariaHidden === 'true') return false;

      // Possible geometric + CSS heuristic to detect big overlay in front of everything
      /*try {
        const rect = el.getBoundingClientRect();
        const vw = window.innerWidth || document.documentElement.clientWidth || 0;
        const vh = window.innerHeight || document.documentElement.clientHeight || 0;
        const viewportArea = vw * vh;
        const elArea = rect.width * rect.height;
        const areaRatio = viewportArea > 0 ? (elArea / viewportArea) : 0;

        const style = window.getComputedStyle(el);
        const position = style.position;
        const zIndex = parseInt(style.zIndex || '0', 10);

        const overlayish = position === 'fixed' || position === 'sticky';
        const coversMuch = areaRatio > 0.25; // covers ≥ 25% of viewport
        const inFront = zIndex >= 1000 || (zIndex >= 100 && coversMuch);

        if (overlayish && coversMuch && inFront) {
          return true;
        }
      } catch (e) {
        // If something goes wrong, just don't treat it as modal
      }*/

      return this.isElementVisible(el);
    },

    isInModal: function (el) {
      let node = el;
      while (node && node !== document.body) {
        if (this.isModalElement(node)) {
          return true;
        }
        node = node.parentElement;
      }
      return false;
    },

    extractLocators: function (el) {
      return {
        role: this.getRole(el),
        xpath: this.getXPath(el),
        text: this.getText(el),
        css: this.getCSS(el),
        placeholder: el.getAttribute('placeholder') || el.getAttribute('aria-placeholder') || '',
        label: this.getLabel(el),
        altText: el.getAttribute('alt') || '',
        title: el.getAttribute('title') || '',
        testId: this.getTestId(el),
        isModal: this.isInModal(el)
      };
    }
  };

  console.log("__locatorUtils enhanced with smarter attributes");
})();
