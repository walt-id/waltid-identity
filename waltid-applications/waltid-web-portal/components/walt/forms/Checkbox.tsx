import {ReactNode} from "react";

type Props = {
  value: boolean;
  onChange: (value: boolean) => void;
  children?: ReactNode;
  [x: string]: any;
};

export default function Checkbox({
  value = false,
  onChange,
  children,
  ...otherProps
}: Props) {
  return (
    <div className="flex flex-row text-left" {...otherProps}>
      <div className="flex h-6 items-center">
        <input
          id="comments"
          aria-describedby="comments-description"
          name="comments"
          type="checkbox"
          checked={value}
          onChange={() => {
            onChange(!value);
          }}
          className="h-4 w-4 rounded border-gray-300 text-indigo-600 focus:ring-indigo-600"
        />
      </div>
      <div className="ml-3 text-sm leading-6">
        <label htmlFor="comments" className="font-medium text-gray-900">
          {children}
        </label>
      </div>
    </div>
  );
}
