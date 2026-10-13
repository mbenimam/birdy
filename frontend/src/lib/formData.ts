/** Text value of a form field; empty when the field is missing or is a file. */
export function readText(formData: FormData, name: string): string {
  const value = formData.get(name);
  return typeof value === 'string' ? value : '';
}
