import { useActionState } from 'react';
import { Link } from 'react-router';
import { createSession } from '../api/accounts';
import { Field } from '../components/Field';
import { useAuth } from './AuthContext';
import { readText } from '../lib/formData';
import { emptyFormState, failedFormState, type FormState } from './formState';

export function LoginForm() {
  const { signIn } = useAuth();

  const [state, submit, pending] = useActionState(async (_previous: FormState, formData: FormData) => {
    const username = readText(formData, 'username').trim();
    try {
      // Signing in flips the auth status; LoginPage then redirects to the page the visitor came for.
      signIn(await createSession({ username, password: readText(formData, 'password') }));
      return emptyFormState;
    } catch (error) {
      return failedFormState(error, { username });
    }
  }, emptyFormState);

  return (
    <form className="login-form" action={submit} aria-labelledby="login-title">
      <h1 id="login-title" className="login-form__title">
        Log in
      </h1>
      {state.message !== null && (
        <p className="login-form__error" role="alert">
          {state.message}
        </p>
      )}
      <Field
        label="Username"
        name="username"
        autoComplete="username"
        autoCapitalize="none"
        required
        defaultValue={state.values.username}
        error={state.fields.username}
      />
      <Field
        label="Password"
        name="password"
        type="password"
        autoComplete="current-password"
        required
        error={state.fields.password}
      />
      <button type="submit" className="login-form__button btn btn--white" disabled={pending}>
        {pending ? 'Logging in…' : 'Log in'}
      </button>
      <p className="login-form__links">
        No account yet? <Link to="/login/signup">Sign up</Link>
      </p>
    </form>
  );
}
