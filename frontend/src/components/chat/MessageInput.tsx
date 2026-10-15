import { useState, useRef, useEffect } from 'react';
import { Send, Database } from 'lucide-react';

interface MessageInputProps {
  onSend: (message: string, useRag: boolean) => void;
  disabled?: boolean;
}

export default function MessageInput({ onSend, disabled }: MessageInputProps) {
  const [message, setMessage] = useState('');
  const [useRag, setUseRag] = useState(true);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
      textareaRef.current.style.height = Math.min(textareaRef.current.scrollHeight, 150) + 'px';
    }
  }, [message]);

  const handleSubmit = (e?: React.FormEvent) => {
    e?.preventDefault();
    const trimmed = message.trim();
    if (!trimmed || disabled) return;
    onSend(trimmed, useRag);
    setMessage('');
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSubmit();
    }
  };

  return (
    <div className="border-t border-gray-200 dark:border-slate-700 bg-white dark:bg-slate-800 p-4">
      <form onSubmit={handleSubmit} className="max-w-4xl mx-auto space-y-2">
        <div className="flex items-center justify-between text-xs px-1">
          <button
            type="button"
            onClick={() => setUseRag(!useRag)}
            className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full font-medium transition border ${
              useRag
                ? 'bg-indigo-50 text-indigo-700 border-indigo-200 dark:bg-indigo-900/40 dark:text-indigo-300 dark:border-indigo-700'
                : 'bg-gray-100 text-gray-500 border-gray-200 dark:bg-slate-700 dark:text-slate-400 dark:border-slate-600'
            }`}
            title="Toggle Retrieval-Augmented Generation using uploaded documents"
          >
            <Database size={13} className={useRag ? 'text-indigo-600 dark:text-indigo-400' : ''} />
            <span>RAG Document Retrieval: {useRag ? 'ON' : 'OFF'}</span>
          </button>
        </div>

        <div className="flex items-end gap-2">
          <div className="flex-1 relative">
            <textarea
              ref={textareaRef}
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Type your message... (Shift+Enter for new line)"
              disabled={disabled}
              rows={1}
              className="w-full resize-none rounded-xl border border-gray-300 dark:border-slate-600 bg-gray-50 dark:bg-slate-700 text-gray-900 dark:text-white px-4 py-3 pr-12 focus:ring-2 focus:ring-indigo-500 focus:border-transparent outline-none transition text-sm disabled:opacity-50"
            />
          </div>
          <button
            type="submit"
            disabled={disabled || !message.trim()}
            className="p-3 bg-indigo-600 hover:bg-indigo-700 disabled:bg-gray-300 dark:disabled:bg-slate-600 text-white rounded-xl transition shrink-0"
          >
            <Send size={18} />
          </button>
        </div>
      </form>
    </div>
  );
}
