() => {
    // 尝试使用注入的脚本
    if (window.__injectedScript) {
        return window.__injectedScript.ariaSnapshot(document.body, {
            forAI: true,
            refPrefix: ''
        });
    }

    // 自实现的基础 ARIA 快照逻辑
    function generateAriaSnapshot(element, options = {}) {
        const { forAI = false, refPrefix = '' } = options;
        
        // ref计数器和缓存
        let refCounter = 0;

        function getAriaRole(element) {
            const explicitRole = element.getAttribute('role');
            if (explicitRole) return explicitRole;

            const tagName = element.tagName.toLowerCase();
            const roleMap = {
                'button': 'button',
                'a': element.href ? 'link' : null,
                'input': getInputRole(element),
                'img': 'image',
                'h1': 'heading', 'h2': 'heading', 'h3': 'heading',
                'h4': 'heading', 'h5': 'heading', 'h6': 'heading',
                'nav': 'navigation',
                'main': 'main',
                'article': 'article',
                'section': 'region',
                'header': 'banner',
                'footer': 'contentinfo',
                'ul': 'list', 'ol': 'list',
                'li': 'listitem',
                'table': 'table',
                'form': 'form',
                'iframe': 'iframe'
            };
            return roleMap[tagName] || null;
        }

        function getInputRole(input) {
            const type = (input.type || 'text').toLowerCase();
            const typeRoleMap = {
                'button': 'button', 'submit': 'button', 'reset': 'button',
                'checkbox': 'checkbox', 'radio': 'radio',
                'range': 'slider', 'text': 'textbox',
                'email': 'textbox', 'password': 'textbox',
                'search': 'searchbox', 'tel': 'textbox', 'url': 'textbox'
            };
            return typeRoleMap[type] || 'textbox';
        }

        function getAccessibleName(element) {
            const ariaLabel = element.getAttribute('aria-label');
            if (ariaLabel) return ariaLabel.trim();

            const ariaLabelledby = element.getAttribute('aria-labelledby');
            if (ariaLabelledby) {
                const labelElements = ariaLabelledby.split(/\\s+/)
                    .map(id => document.getElementById(id))
                    .filter(Boolean);
                if (labelElements.length > 0) {
                    return labelElements.map(el => el.textContent?.trim()).join(' ');
                }
            }

            if (element.id) {
                const label = document.querySelector(`label[for="${element.id}"]`);
                if (label) return label.textContent?.trim();
            }

            const parentLabel = element.closest('label');
            if (parentLabel) {
                return parentLabel.textContent?.replace(element.value || '', '').trim();
            }

            const alt = element.getAttribute('alt');
            if (alt) return alt.trim();

            const title = element.getAttribute('title');
            if (title) return title.trim();

            const textContent = element.textContent?.trim();
            if (textContent && textContent.length < 100) {
                return textContent;
            }

            return '';
        }

        function isElementVisible(element) {
            if (!element.offsetParent && element.tagName !== 'BODY') return false;

            const style = window.getComputedStyle(element);
            return !(style.display === 'none' ||
                style.visibility === 'hidden' ||
                style.opacity === '0');
        }

        function receivesPointerEvents(element) {
            if (!isElementVisible(element)) return false;
            const style = window.getComputedStyle(element);
            if (style.pointerEvents === 'none') return false;
            
            // 检查是否是可交互元素
            const tagName = element.tagName.toLowerCase();
            const interactiveTags = ['button', 'a', 'input', 'select', 'textarea', 'iframe'];
            if (interactiveTags.includes(tagName)) return true;
            
            // 检查是否有点击事件监听器或者有tabindex
            if (element.hasAttribute('onclick') || 
                element.hasAttribute('tabindex') ||
                element.getAttribute('role') === 'button' ||
                element.getAttribute('role') === 'link') {
                return true;
            }
            
            // 检查是否有pointer光标
            if (style.cursor === 'pointer') return true;
            
            return false;
        }

        function hasPointerCursor(element) {
            const style = window.getComputedStyle(element);
            return style.cursor === 'pointer';
        }

        function generateRef(element, role, name) {
            if (!forAI) return null;
            
            // 检查元素是否已经有ref缓存
            if (element._ariaRef && element._ariaRef.role === role && element._ariaRef.name === name) {
                return element._ariaRef.ref;
            }
            
            // 生成新的ref
            const ref = `${refPrefix}e${++refCounter}`;
            element._ariaRef = { role, name, ref };
            return ref;
        }

        function buildAriaTree(element, depth = 0) {
            if (!isElementVisible(element)) return null;

            const role = getAriaRole(element);
            if (!role && !forAI) return null;

            const name = getAccessibleName(element);
            const indent = '  '.repeat(depth);

            let line = `${indent}- ${role || element.tagName.toLowerCase()}`;

            if (name) {
                line += ` "${name}"`;
            }

            const states = [];
            
            // 检查checked状态
            const checked = element.getAttribute('aria-checked') || (element.checked ? 'true' : null);
            if (checked === 'mixed') states.push('checked=mixed');
            else if (checked === 'true') states.push('checked');
            
            // 检查disabled状态
            if (element.disabled || element.getAttribute('aria-disabled') === 'true') {
                states.push('disabled');
            }
            
            // 检查expanded状态
            const expanded = element.getAttribute('aria-expanded');
            if (expanded === 'true') states.push('expanded');
            
            // 检查pressed状态
            const pressed = element.getAttribute('aria-pressed');
            if (pressed === 'mixed') states.push('pressed=mixed');
            else if (pressed === 'true') states.push('pressed');
            
            // 检查selected状态
            if (element.selected || element.getAttribute('aria-selected') === 'true') {
                states.push('selected');
            }

            if (states.length > 0) {
                line += ` [${states.join(' ')}]`;
            }

            // 为可交互元素生成ref
            if (receivesPointerEvents(element)) {
                const ref = generateRef(element, role || element.tagName.toLowerCase(), name);
                const cursor = hasPointerCursor(element) ? ' [cursor=pointer]' : '';
                if (ref) {
                    line += ` [ref=${ref}]${cursor}`;
                }
            }

            const result = [line];

            for (const child of element.children) {
                const childTree = buildAriaTree(child, depth + 1);
                if (childTree) {
                    result.push(...childTree);
                }
            }

            return result;
        }

        const tree = buildAriaTree(element);
        return tree ? tree.join('\\n') : '';
    }

    return generateAriaSnapshot(document.body, { forAI: true, refPrefix: '' });
}