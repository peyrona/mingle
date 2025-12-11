/**
 * Represents a code editor component for modifying dashboard HTML and JavaScript.
 * This class provides an interface within a specified container element
 * for users to view and edit the raw HTML structure and JavaScript logic
 * associated with a dashboard gadget.
 */
class Coder {
    /**
     * Creates an instance of Coder.
     * @param {HTMLElement} containerElement - The DOM element where the code editor will be rendered.
     * @param {string} initialHtmlContent - The initial HTML content to display in the editor.
     * @param {string} initialJsContent - The initial JavaScript content to display in the editor.
     * @param {function(string, string): void} onApplyCallback - A callback function to be executed when changes are applied.
     *                                                              It receives the updated HTML and JavaScript content as arguments.
     */
    constructor(containerElement, initialHtmlContent, initialJsContent, onApplyCallback) {
        if (!containerElement || !(containerElement instanceof HTMLElement)) {
            console.error("Coder: Invalid containerElement provided.");
            return;
        }

        /** @private @type {HTMLElement} */
        this._containerElement = containerElement;
        /** @private @type {string} */
        this._currentHtmlContent = initialHtmlContent || '';
        /** @private @type {string} */
        this._currentJsContent = initialJsContent || '';
        /** @private @type {function(string, string): void} */
        this._onApplyCallback = onApplyCallback || (() => {});

        /** @private @type {HTMLTextAreaElement|null} */
        this._htmlEditor = null;
        /** @private @type {HTMLTextAreaElement|null} */
        this._jsEditor = null;

        this._renderUI();
    }

    /**
     * Renders the user interface for the code editor within the container element.
     * @private
     */
    _renderUI() {
        this._containerElement.innerHTML = ''; // Clear existing content

        // --- HTML Editor Section ---
        const htmlGroup = document.createElement('div');
        htmlGroup.className = 'code-editor-group'; // Use a class for styling
        htmlGroup.innerHTML = '<h3>HTML Content</h3>';

        this._htmlEditor = document.createElement('textarea');
        this._htmlEditor.className = 'code-editor html-editor'; // Use classes for styling
        this._htmlEditor.value = this._currentHtmlContent;
        this._htmlEditor.placeholder = 'Enter HTML content here...';
        htmlGroup.appendChild(this._htmlEditor);
        this._containerElement.appendChild(htmlGroup);

        // --- JavaScript Editor Section ---
        const jsGroup = document.createElement('div');
        jsGroup.className = 'code-editor-group';
        jsGroup.innerHTML = '<h3>JavaScript Content</h3>';

        this._jsEditor = document.createElement('textarea');
        this._jsEditor.className = 'code-editor js-editor';
        this._jsEditor.value = this._currentJsContent;
        this._jsEditor.placeholder = 'Enter JavaScript code here...';
        jsGroup.appendChild(this._jsEditor);
        this._containerElement.appendChild(jsGroup);

        // --- Apply Button ---
        const applyButton = document.createElement('button');
        applyButton.textContent = 'Apply Changes';
        applyButton.className = 'apply-changes-button'; // Class for styling
        applyButton.addEventListener('click', () => this._applyChanges());
        this._containerElement.appendChild(applyButton);

        // Basic styling for the editors (can be moved to CSS file)
        const style = document.createElement('style');
        style.textContent = `
            .code-editor-group {
                margin-bottom: 15px;
            }
            .code-editor {
                width: 100%;
                min-height: 200px;
                padding: 10px;
                border: 1px solid #ccc;
                border-radius: 4px;
                font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, Courier, monospace;
                font-size: 14px;
                line-height: 1.5;
                resize: vertical; /* Allow vertical resizing */
                box-sizing: border-box; /* Include padding and border in the element's total width and height */
            }
            .html-editor {
                background-color: #f8f8f8;
                color: #333;
            }
            .js-editor {
                background-color: #f0f0f0;
                color: #222;
            }
            .apply-changes-button {
                padding: 10px 20px;
                background-color: #007bff;
                color: white;
                border: none;
                border-radius: 5px;
                cursor: pointer;
                font-size: 16px;
            }
            .apply-changes-button:hover {
                background-color: #0056b3;
            }
        `;
        this._containerElement.appendChild(style); // Append style to container to keep it self-contained
    }

    /**
     * Applies the changes made in the editors.
     * This method retrieves the current content from the HTML and JavaScript text areas
     * and executes the `onApplyCallback` with these new values.
     * @private
     */
    _applyChanges() {
        if (this._htmlEditor && this._jsEditor) {
            const newHtml = this._htmlEditor.value;
            const newJs = this._jsEditor.value;
            this._currentHtmlContent = newHtml;
            this._currentJsContent = newJs;
            this._onApplyCallback(newHtml, newJs);
        }
    }

    /**
     * Updates the content displayed in the HTML and JavaScript editors.
     * This is useful if the underlying gadget's code changes externally.
     * @param {string} newHtml - The new HTML content to display.
     * @param {string} newJs - The new JavaScript content to display.
     */
    updateContent(newHtml, newJs) {
        this._currentHtmlContent = newHtml || '';
        this._currentJsContent = newJs || '';
        if (this._htmlEditor) {
            this._htmlEditor.value = this._currentHtmlContent;
        }
        if (this._jsEditor) {
            this._jsEditor.value = this._currentJsContent;
        }
    }

    /**
     * Retrieves the current HTML content from the editor.
     * @returns {string} The current HTML content.
     */
    getHtmlContent() {
        return this._htmlEditor ? this._htmlEditor.value : this._currentHtmlContent;
    }

    /**
     * Retrieves the current JavaScript content from the editor.
     * @returns {string} The current JavaScript content.
     */
    getJsContent() {
        return this._jsEditor ? this._jsEditor.value : this._currentJsContent;
    }
}
