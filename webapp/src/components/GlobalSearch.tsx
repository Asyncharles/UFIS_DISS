import { FormEvent, useEffect, useState } from "react";

type GlobalSearchProps = {
  initialValue?: string;
  placeholder?: string;
  autoFocus?: boolean;
  onSearch: (query: string) => void;
};

export function GlobalSearch({
  initialValue = "",
  placeholder = "Search...",
  autoFocus = false,
  onSearch,
}: GlobalSearchProps) {
  const [value, setValue] = useState(initialValue);

  useEffect(() => {
    setValue(initialValue);
  }, [initialValue]);

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    onSearch(value.trim());
  }

  return (
    <form className="search-form" onSubmit={handleSubmit}>
      <input
        autoFocus={autoFocus}
        className="search-input"
        onChange={(event) => setValue(event.target.value)}
        placeholder={placeholder}
        type="search"
        value={value}
      />
      <button className="btn-primary" type="submit">
        Find
      </button>
    </form>
  );
}
