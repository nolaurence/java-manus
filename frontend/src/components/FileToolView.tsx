import React, {
  useState,
  useEffect,
  useRef,
  forwardRef,
  useImperativeHandle,
} from "react";
import * as monaco from "monaco-editor/esm/vs/editor/editor.api";

// Import languages
import "monaco-editor/esm/vs/language/json/monaco.contribution";
import "monaco-editor/esm/vs/basic-languages/javascript/javascript.contribution";
import "monaco-editor/esm/vs/basic-languages/typescript/typescript.contribution";
import "monaco-editor/esm/vs/basic-languages/html/html.contribution";
import "monaco-editor/esm/vs/basic-languages/css/css.contribution";
import "monaco-editor/esm/vs/basic-languages/python/python.contribution";
import "monaco-editor/esm/vs/basic-languages/java/java.contribution";
import "monaco-editor/esm/vs/basic-languages/go/go.contribution";
import "monaco-editor/esm/vs/basic-languages/markdown/markdown.contribution";
import type { ToolContent } from '@/types/message';

import { viewFile } from "@/services/api/sandbox";
import { message } from 'antd';

// type ToolContent = {
//   args: {
//     file: string;
//   };
// };

export type FileViewerHandle = {
  loadContent: () => void;
};

type Props = {
  agentId: string;
  toolContent: ToolContent;
};

const FileViewer = forwardRef<FileViewerHandle, Props>(
  ({ agentId, toolContent }, ref) => {
    const [fileContent, setFileContent] = useState<string>("");
    const monacoContainer = useRef<HTMLDivElement | null>(null);
    const editorRef = useRef<monaco.editor.IStandaloneCodeEditor | null>(null);
    const refreshInterval = useRef<number | null>(null);

    const filePath = toolContent?.args?.file || "";
    const fileName = filePath.split("/").pop() || "";

    // Infer language based on filename
    const getLanguage = (filename: string): string => {
      const extension = filename.split(".").pop()?.toLowerCase() || "";
      const languageMap: Record<string, string> = {
        js: "javascript",
        ts: "typescript",
        html: "html",
        css: "css",
        json: "json",
        py: "python",
        java: "java",
        c: "c",
        cpp: "cpp",
        go: "go",
        md: "markdown",
        txt: "plaintext",
        vue: "html",
        jsx: "javascript",
        tsx: "typescript",
      };

      return languageMap[extension] || "plaintext";
    };

    // Initialize Monaco Editor
    const initMonacoEditor = () => {
      if (monacoContainer.current && !editorRef.current) {
        const language = getLanguage(filePath);

        editorRef.current = monaco.editor.create(monacoContainer.current, {
          value: "",
          language,
          theme: "vs",
          readOnly: true,
          minimap: { enabled: false },
          scrollBeyondLastLine: false,
          automaticLayout: true,
          lineNumbers: "off",
          wordWrap: "on",
          scrollbar: {
            vertical: "auto",
            horizontal: "auto",
          },
        });
      }
    };

    // Load file content
    const loadFileContent = async () => {
      if (!filePath) return;

      try {
        const response = await viewFile(agentId, filePath);
        if (fileContent !== response.content) {
          setFileContent(response.content);
          if (editorRef.current) {
            const model = editorRef.current.getModel();
            if (model) {
              model.setValue(fileContent);
            } else {
              editorRef.current.setValue(fileContent);
            }
            monaco.editor.setModelLanguage(
              editorRef.current.getModel()!,
              getLanguage(filePath)
            );
          }
        }
      } catch (error) {
        console.error("Failed to load file content:", error);
        message.error("Failed to load file content");
      }
    };

    // Expose method to parent component
    useImperativeHandle(ref, () => ({
      loadContent: () => {
        loadFileContent();
      },
    }));

    // Mount effect
    useEffect(() => {
      initMonacoEditor();
      loadFileContent();

      refreshInterval.current = window.setInterval(() => {
        loadFileContent();
      }, 5000);

      return () => {
        if (refreshInterval.current) {
          clearInterval(refreshInterval.current);
        }

        if (editorRef.current) {
          editorRef.current.dispose();
          editorRef.current = null;
        }
      };
    }, [filePath]);

    return (
      <div>
        {/* Header */}
        <div
          className="h-[36px] flex items-center px-3 w-full bg-[var(--background-gray-main)] border-b border-[var(--border-main)] rounded-t-[12px] shadow-[inset_0px_1px_0px_0px_#FFFFFF] dark:shadow-[inset_0px_1px_0px_0px_#FFFFFF30]"
        >
          <div className="flex-1 flex items-center justify-center">
            <div
              className="max-w-[250px] truncate text-[var(--text-tertiary)] text-sm font-medium text-center"
            >
              {fileName}
            </div>
          </div>
        </div>

        {/* Content */}
        <div className="flex-1 min-h-0 w-full overflow-y-auto">
          <div dir="ltr" data-orientation="horizontal" className="flex flex-col min-h-0 h-full relative">
            <div
              data-state="active"
              data-orientation="horizontal"
              role="tabpanel"
              id={`radix-:r2ke:-content-${filePath}`}
              tabIndex={0}
              className="focus-visible:outline-none data-[state=inactive]:hidden flex-1 min-h-0 h-full text-sm flex flex-col py-0 outline-none overflow-auto"
            >
              <section style={{ display: "flex", position: "relative", textAlign: "initial", width: "100%", height: "100%" }}>
                <div ref={monacoContainer} style={{ width: "100%", height: "100%" }}></div>
              </section>
            </div>
          </div>
        </div>
      </div>
    );
  }
);

FileViewer.displayName = "FileViewer";

export default FileViewer;
